package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Stage 3: slices a block into rectangular lots with guillotine cuts.
 *
 * From every road-facing edge of the block, a strip of lot depth is cut
 * inward, then the strip is cut perpendicular into individual lots of
 * randomized width. Whatever interior remains after all road-facing strips
 * are taken has no road access by definition - it becomes park land.
 *
 * Non-rectangular blocks (roads only partially crossing, map corners) are
 * handled by carving their largest inscribed rectangle first and re-queueing
 * the leftover pieces as smaller regions.
 */
@Component
public class LotSubdivisionService {

    /**
     * @param lots       road-fronting parcels, ready for zoning
     * @param parkCells  interior cells without road access - become PARK tiles
     * @param unusedCells degenerate slivers - become UNUSED tiles
     */
    public record SubdivisionResult(List<LotDraft> lots, List<GridPosition> parkCells, List<GridPosition> unusedCells) {
    }

    public SubdivisionResult subdivide(Block block, TileType[][] tiles, RoadClass[][] roadClasses,
                                       GenerationConfig config, Random random) {
        List<LotDraft> lots = new ArrayList<>();
        List<GridPosition> parkCells = new ArrayList<>();
        List<GridPosition> unusedCells = new ArrayList<>();

        Deque<List<GridPosition>> regions = new ArrayDeque<>();
        regions.add(block.cells());

        while (!regions.isEmpty()) {
            List<GridPosition> region = regions.poll();
            if (region.size() < config.minBlockArea()) {
                unusedCells.addAll(region);
                continue;
            }

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (GridPosition cell : region) {
                minX = Math.min(minX, cell.getX());
                minY = Math.min(minY, cell.getY());
                maxX = Math.max(maxX, cell.getX());
                maxY = Math.max(maxY, cell.getY());
            }

            boolean rectangular = region.size() == (maxX - minX + 1) * (maxY - minY + 1);
            if (rectangular) {
                carveRectangle(minX, minY, maxX, maxY, tiles, roadClasses, config, random, lots, parkCells);
            } else {
                // Guillotine cuts assume a rectangle: take the largest one that
                // fits inside this region, carve it, and re-queue the leftovers
                // as their own (smaller) regions.
                int[] rect = RegionUtils.largestInscribedRectangle(region);
                carveRectangle(rect[0], rect[1], rect[2], rect[3], tiles, roadClasses, config, random, lots, parkCells);
                regions.addAll(leftoverComponents(region, rect));
            }
        }
        return new SubdivisionResult(lots, parkCells, unusedCells);
    }

    /**
     * Cuts lot strips inward from every road-adjacent edge of the rectangle
     * until no road-facing edge remains; the rest is interior park land.
     */
    private void carveRectangle(int x0, int y0, int x1, int y1,
                                TileType[][] tiles, RoadClass[][] roadClasses,
                                GenerationConfig config, Random random,
                                List<LotDraft> lots, List<GridPosition> parkCells) {
        int rx0 = x0, ry0 = y0, rx1 = x1, ry1 = y1;

        while (rx0 <= rx1 && ry0 <= ry1) {
            List<Direction> roadEdges = roadAdjacentEdges(rx0, ry0, rx1, ry1, tiles);
            if (roadEdges.isEmpty()) {
                collectCells(rx0, ry0, rx1, ry1, parkCells);
                return;
            }

            Direction edge = roadEdges.get(random.nextInt(roadEdges.size()));
            switch (edge) {
                case NORTH -> {
                    int stripDepth = stripDepth(ry1 - ry0 + 1, config);
                    cutStrip(rx0, ry0, rx1, ry0 + stripDepth - 1, true, tiles, roadClasses, config, random, lots, parkCells);
                    ry0 += stripDepth;
                }
                case SOUTH -> {
                    int stripDepth = stripDepth(ry1 - ry0 + 1, config);
                    cutStrip(rx0, ry1 - stripDepth + 1, rx1, ry1, true, tiles, roadClasses, config, random, lots, parkCells);
                    ry1 -= stripDepth;
                }
                case WEST -> {
                    int stripDepth = stripDepth(rx1 - rx0 + 1, config);
                    cutStrip(rx0, ry0, rx0 + stripDepth - 1, ry1, false, tiles, roadClasses, config, random, lots, parkCells);
                    rx0 += stripDepth;
                }
                case EAST -> {
                    int stripDepth = stripDepth(rx1 - rx0 + 1, config);
                    cutStrip(rx1 - stripDepth + 1, ry0, rx1, ry1, false, tiles, roadClasses, config, random, lots, parkCells);
                    rx1 -= stripDepth;
                }
            }
        }
    }

    /** Strip depth, absorbing the remainder when what would be left is too thin to ever hold lots. */
    private int stripDepth(int available, GenerationConfig config) {
        int depth = Math.min(config.stripDepth(), available);
        if (available - depth < config.minLotWidth()) {
            depth = available;
        }
        return depth;
    }

    /**
     * Splits one strip perpendicular to its road edge into individual lots.
     * horizontal = true means the strip runs along x (cut at x intervals).
     */
    private void cutStrip(int x0, int y0, int x1, int y1, boolean horizontal,
                          TileType[][] tiles, RoadClass[][] roadClasses,
                          GenerationConfig config, Random random,
                          List<LotDraft> lots, List<GridPosition> parkCells) {
        int from = horizontal ? x0 : y0;
        int to = horizontal ? x1 : y1;

        int pos = from;
        List<int[]> spans = new ArrayList<>();
        while (pos <= to) {
            int width = config.minLotWidth()
                    + random.nextInt(config.maxLotWidth() - config.minLotWidth() + 1);
            // The last remainder merges into this lot if it would be too narrow.
            if (to - (pos + width - 1) < config.minLotWidth()) {
                width = to - pos + 1;
            }
            spans.add(new int[]{pos, pos + width - 1});
            pos += width;
        }

        for (int[] span : spans) {
            int lx0 = horizontal ? span[0] : x0;
            int lx1 = horizontal ? span[1] : x1;
            int ly0 = horizontal ? y0 : span[0];
            int ly1 = horizontal ? y1 : span[1];

            Map<Direction, Frontage> frontages =
                    computeFrontages(lx0, ly0, lx1 - lx0 + 1, ly1 - ly0 + 1, tiles, roadClasses);
            if (frontages.isEmpty()) {
                // The strip edge only partially bordered road (e.g. map edge
                // stretch); a lot without any actual road access becomes park.
                collectCells(lx0, ly0, lx1, ly1, parkCells);
            } else {
                lots.add(new LotDraft(lx0, ly0, lx1 - lx0 + 1, ly1 - ly0 + 1, frontages));
            }
        }
    }

    /** All four sides of the lot, measured directly against the road tiles beyond each side. */
    private Map<Direction, Frontage> computeFrontages(int x, int y, int width, int depth,
                                                      TileType[][] tiles, RoadClass[][] roadClasses) {
        Map<Direction, Frontage> frontages = new EnumMap<>(Direction.class);
        int gridW = tiles.length;
        int gridH = tiles[0].length;

        for (Direction dir : Direction.values()) {
            int length = 0;
            RoadClass best = null;
            boolean vertical = dir == Direction.NORTH || dir == Direction.SOUTH;
            int span = vertical ? width : depth;

            for (int i = 0; i < span; i++) {
                int cx = vertical ? x + i : (dir == Direction.WEST ? x - 1 : x + width);
                int cy = vertical ? (dir == Direction.NORTH ? y - 1 : y + depth) : y + i;
                if (cx < 0 || cx >= gridW || cy < 0 || cy >= gridH || tiles[cx][cy] != TileType.ROAD) {
                    continue;
                }
                length++;
                RoadClass roadClass = roadClasses[cx][cy];
                if (best == null || roadClass.ordinal() < best.ordinal()) {
                    best = roadClass;
                }
            }
            if (length > 0) {
                frontages.put(dir, new Frontage(length, best));
            }
        }
        return frontages;
    }

    private List<Direction> roadAdjacentEdges(int x0, int y0, int x1, int y1, TileType[][] tiles) {
        List<Direction> edges = new ArrayList<>();
        int gridW = tiles.length;
        int gridH = tiles[0].length;

        if (y0 - 1 >= 0 && anyRoadInRow(x0, x1, y0 - 1, tiles)) edges.add(Direction.NORTH);
        if (y1 + 1 < gridH && anyRoadInRow(x0, x1, y1 + 1, tiles)) edges.add(Direction.SOUTH);
        if (x0 - 1 >= 0 && anyRoadInColumn(y0, y1, x0 - 1, tiles)) edges.add(Direction.WEST);
        if (x1 + 1 < gridW && anyRoadInColumn(y0, y1, x1 + 1, tiles)) edges.add(Direction.EAST);
        return edges;
    }

    private boolean anyRoadInRow(int x0, int x1, int y, TileType[][] tiles) {
        for (int x = x0; x <= x1; x++) {
            if (tiles[x][y] == TileType.ROAD) return true;
        }
        return false;
    }

    private boolean anyRoadInColumn(int y0, int y1, int x, TileType[][] tiles) {
        for (int y = y0; y <= y1; y++) {
            if (tiles[x][y] == TileType.ROAD) return true;
        }
        return false;
    }

    private void collectCells(int x0, int y0, int x1, int y1, List<GridPosition> target) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                target.add(new GridPosition(x, y));
            }
        }
    }

    /** Region cells outside the carved rectangle, regrouped into connected components. */
    private List<List<GridPosition>> leftoverComponents(List<GridPosition> region, int[] rect) {
        Set<Long> leftover = new HashSet<>();
        for (GridPosition cell : region) {
            boolean inRect = cell.getX() >= rect[0] && cell.getX() <= rect[2]
                    && cell.getY() >= rect[1] && cell.getY() <= rect[3];
            if (!inRect) {
                leftover.add(pack(cell.getX(), cell.getY()));
            }
        }

        List<List<GridPosition>> components = new ArrayList<>();
        while (!leftover.isEmpty()) {
            long start = leftover.iterator().next();
            List<GridPosition> component = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            leftover.remove(start);

            while (!queue.isEmpty()) {
                long packed = queue.poll();
                int cx = (int) (packed >> 32);
                int cy = (int) packed;
                component.add(new GridPosition(cx, cy));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long neighbor = pack(cx + d[0], cy + d[1]);
                    if (leftover.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
