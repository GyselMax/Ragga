package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Stage 1: lays down the road network, roads-first.
 *
 * Arterials are "wandering" roads: they cross the whole map but drift
 * sideways as they go (a random walk with momentum, bounded around their
 * base line). That gives the organic, slightly bent main streets of a real
 * city instead of a monotone checkerboard - the simple version of how
 * organic city generators work (full versions grow roads with L-systems or
 * tensor fields; the meander is the cheap 90% of the visual effect).
 *
 * The space between arterials is then split by straight collector/local
 * roads, region by region: flood-fill the untyped space, and any region
 * still large enough gets a road cut through the middle of its largest
 * inscribed rectangle. Denser parts of the city split more often, so
 * downtown gets small blocks and the outskirts keep large ones.
 *
 * Stateless: config and randomness are passed in per call so the same
 * component works standalone (GridVisualizer) and as a Spring bean.
 */
@Component
public class RoadNetworkGenerator {

    // Wandering-arterial rhythm: how long a drift direction holds. How often
    // a road sidesteps and how FAR it may stray are density-scaled at runtime
    // (crooked in the core, straight at the rim), around the configurable
    // bound GenerationConfig.arterialMaxDrift.
    private static final int DRIFT_HOLD_MIN = 4;
    private static final int DRIFT_HOLD_VARIATION = 5;

    // Urban grid gaps: per arterial, up to this many stretches of one to two
    // block lengths are skipped inside the city, merging the neighboring
    // blocks into longer rectangles - not every square gets all four roads.
    private static final int MAX_GRID_GAPS = 2;

    public RoadNetwork generate(GenerationConfig config, DensityField density, TerrainResult terrain,
                                SettlementPlan settlements, Random random) {
        // The terrain's pre-seeded grid IS the map array: water and banks are
        // non-null, so splits/blocks/lots never see them. Only arterials
        // overwrite water - those tiles are the bridges.
        TileType[][] tiles = terrain.tiles();
        RoadClass[][] roadClasses = new RoadClass[config.width()][config.height()];
        List<RoadDraft> roads = new ArrayList<>();

        // Grid arterials: drawn only on urban land (city cores + hamlet
        // bumps). Countryside roads come exclusively from the settlement plan.
        for (int base : arterialPositions(config.width(), config, random)) {
            wanderArterial(base, true, config, density, terrain, random, roads, tiles, roadClasses);
        }
        for (int base : arterialPositions(config.height(), config, random)) {
            wanderArterial(base, false, config, density, terrain, random, roads, tiles, roadClasses);
        }

        // Destination-driven countryside roads: doglegs between settlements
        // and out to the map edges, so a rural road always goes somewhere.
        for (int[] road : settlements.roads()) {
            drawConnectionRoad(road[0], road[1], road[2], road[3], terrain, roads, tiles, roadClasses);
        }

        splitBlocks(config, density, random, roads, tiles, roadClasses);
        demoteDisconnected(tiles, roadClasses, config, density, roads);
        return new RoadNetwork(roads, tiles, roadClasses);
    }

    /**
     * Draws an axis-aligned L-dogleg of ARTERIAL road between two settlement
     * centers (or a center and a border exit): one straight leg along the
     * larger delta, then the perpendicular leg, sharing the corner tile so
     * the run stays 4-connected. Water tiles are drawn as bridges/causeways -
     * connectivity beats avoiding the occasional short river crossing.
     */
    private void drawConnectionRoad(int ax, int ay, int bx, int by, TerrainResult terrain,
                                    List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        // Longer axis first reads as the "main direction" of the road.
        boolean horizontalFirst = Math.abs(bx - ax) >= Math.abs(by - ay);
        int cornerX = horizontalFirst ? bx : ax;
        int cornerY = horizontalFirst ? ay : by;
        drawStraightRun(ax, ay, cornerX, cornerY, roads, tiles, roadClasses);
        drawStraightRun(cornerX, cornerY, bx, by, roads, tiles, roadClasses);
    }

    /** One straight axis-aligned ARTERIAL run (inclusive), recorded as a segment. */
    private void drawStraightRun(int x0, int y0, int x1, int y1,
                                 List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        int fromX = Math.min(x0, x1);
        int toX = Math.max(x0, x1);
        int fromY = Math.min(y0, y1);
        int toY = Math.max(y0, y1);
        roads.add(new RoadDraft(fromX, fromY, toX, toY, RoadClass.ARTERIAL));
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                tiles[x][y] = TileType.ROAD;
                if (roadClasses[x][y] == null || RoadClass.ARTERIAL.ordinal() < roadClasses[x][y].ordinal()) {
                    roadClasses[x][y] = RoadClass.ARTERIAL;
                }
            }
        }
    }

    private List<Integer> arterialPositions(int extent, GenerationConfig config, Random random) {
        // First arterial at half spacing from the edge so the map border only
        // gets a thin roadless band (greenbelt) instead of a full-size one.
        List<Integer> positions = new ArrayList<>();
        int pos = config.arterialSpacing() / 2;
        while (pos <= extent - config.minLotWidth() - 1) {
            int jitter = random.nextInt(config.arterialJitter() * 2 + 1) - config.arterialJitter();
            positions.add(Math.clamp(pos + jitter, 1, extent - 2));
            pos += config.arterialSpacing();
        }
        return positions;
    }

    /**
     * Walks an arterial across the map one row/column at a time. Each step
     * may shift the road one tile sideways; the drift direction is held for
     * several steps (momentum) so the road bends gently instead of
     * zigzagging, and it is pulled back toward its base line so parallel
     * arterials can't wander into each other.
     *
     * The wander is density-scaled: streets bend visibly inside the old core
     * (medieval irregularity) and straighten toward the planned outskirts.
     *
     * Not every walked tile is drawn: only urban land gets a grid (the
     * countryside is served by the settlement plan's connection roads), and
     * inside the city each arterial may skip a couple of block-length gaps
     * (see shouldDraw). The walk itself always runs the full map so parallel
     * arterials keep their spacing rhythm regardless.
     */
    private void wanderArterial(int basePos, boolean vertical,
                                GenerationConfig config, DensityField density, TerrainResult terrain,
                                Random random,
                                List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        int length = vertical ? config.height() : config.width();
        int crossExtent = vertical ? config.width() : config.height();
        List<int[]> gaps = rollGridGaps(length, config, random);

        int pos = basePos;
        int driftDirection = 0;
        int driftHold = 0;
        int runStart = 0;

        for (int step = 0; step < length; step++) {
            if (driftHold == 0) {
                // New drift heading: random, but biased back toward the base
                // line the further the road has strayed.
                int pull = Integer.signum(basePos - pos);
                driftDirection = switch (random.nextInt(3)) {
                    case 0 -> pull == 0 ? (random.nextBoolean() ? 1 : -1) : pull;
                    case 1 -> 0;
                    default -> random.nextBoolean() ? 1 : -1;
                };
                driftHold = DRIFT_HOLD_MIN + random.nextInt(DRIFT_HOLD_VARIATION);
            }
            driftHold--;

            double d = density.at(vertical ? pos : step, vertical ? step : pos);
            double stepChance = 0.10 + 0.35 * d;
            int maxDrift = (int) Math.round(config.arterialMaxDrift() * (0.5 + d));

            boolean shift = driftDirection != 0
                    && random.nextDouble() < stepChance
                    && Math.abs(pos + driftDirection - basePos) <= maxDrift
                    && pos + driftDirection >= 1
                    && pos + driftDirection <= crossExtent - 2;

            if (shift) {
                // Close the straight run INCLUDING the current step's row -
                // that tile is the sideways connector keeping the road
                // 4-connected through the bend, and it must belong to a
                // segment (tiles are always derived from segments; a tile
                // owned by no segment would vanish when Phase B re-derives
                // tiles from persisted RoadSegments).
                addSegment(pos, runStart, step, vertical, gaps, config, density, terrain,
                        roads, tiles, roadClasses);
                pos += driftDirection;
                runStart = step;
            }
        }
        addSegment(pos, runStart, length - 1, vertical, gaps, config, density, terrain,
                roads, tiles, roadClasses);
    }

    /**
     * Adds a straight arterial run, split into the sub-runs that actually
     * pass the draw filter; coordinates are (cross, along) pairs translated
     * per orientation.
     */
    private void addSegment(int cross, int along0, int along1, boolean vertical,
                            List<int[]> gaps, GenerationConfig config, DensityField density,
                            TerrainResult terrain,
                            List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        int drawStart = -1;
        for (int along = along0; along <= along1 + 1; along++) {
            boolean draw = along <= along1
                    && shouldDraw(cross, along, vertical, gaps, config, density, terrain, tiles);
            if (draw && drawStart < 0) {
                drawStart = along;
            } else if (!draw && drawStart >= 0) {
                roads.add(vertical
                        ? new RoadDraft(cross, drawStart, cross, along - 1, RoadClass.ARTERIAL)
                        : new RoadDraft(drawStart, cross, along - 1, cross, RoadClass.ARTERIAL));
                for (int a = drawStart; a < along; a++) {
                    markTile(cross, a, vertical, tiles, roadClasses);
                }
                drawStart = -1;
            }
        }
    }

    /**
     * Grid arterials draw only on urban land - city cores and hamlet bumps.
     * The countryside is left to the settlement plan's connection roads, so
     * a rural road always goes somewhere. Water is only drawn over by
     * arterials PERPENDICULAR to the river (those tiles are the bridges);
     * parallel arterials break where the meander crosses their line and
     * resume beyond, so the riverside keeps its full road density without
     * roads running lengthwise in the water. On urban land everything else is
     * drawn except the rolled grid gaps, which merge neighboring blocks into
     * longer rectangles - bridges are never gapped.
     */
    private boolean shouldDraw(int cross, int along, boolean vertical,
                               List<int[]> gaps, GenerationConfig config, DensityField density,
                               TerrainResult terrain, TileType[][] tiles) {
        int x = vertical ? cross : along;
        int y = vertical ? along : cross;
        if (density.at(x, y) < config.farmlandDensityThreshold()) {
            return false;
        }
        if (tiles[x][y] == TileType.WATER) {
            return terrain.mayBridge(vertical);
        }
        for (int[] gap : gaps) {
            if (along >= gap[0] && along <= gap[1]) {
                return false;
            }
        }
        return true;
    }

    /** Up to MAX_GRID_GAPS skipped stretches of one to two block lengths per arterial. */
    private List<int[]> rollGridGaps(int length, GenerationConfig config, Random random) {
        List<int[]> gaps = new ArrayList<>();
        int count = random.nextInt(MAX_GRID_GAPS + 1);
        for (int i = 0; i < count; i++) {
            int gapLength = config.arterialSpacing() + random.nextInt(config.arterialSpacing() + 1);
            int start = random.nextInt(Math.max(1, length - gapLength));
            gaps.add(new int[]{start, start + gapLength - 1});
        }
        return gaps;
    }

    private void markTile(int cross, int along, boolean vertical, TileType[][] tiles, RoadClass[][] roadClasses) {
        int x = vertical ? cross : along;
        int y = vertical ? along : cross;
        tiles[x][y] = TileType.ROAD;
        if (roadClasses[x][y] == null || RoadClass.ARTERIAL.ordinal() < roadClasses[x][y].ordinal()) {
            roadClasses[x][y] = RoadClass.ARTERIAL;
        }
    }

    /**
     * Splits the space between roads, region by region, for up to
     * maxLocalRoadDepth rounds: every untyped region that is still large
     * enough gets a straight road cut through the middle of its largest
     * inscribed rectangle, along the longer axis. The cut marks every region
     * cell on that line, so it always runs wall-to-wall and ends against the
     * bounding roads. Split probability scales with local density - downtown
     * splits into small blocks, the outskirts keep large ones (which is also
     * where the interior parks come from).
     */
    private void splitBlocks(GenerationConfig config, DensityField density, Random random,
                             List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        // One round beyond the configured depth runs for the dense core only:
        // downtown blocks split once more than the rest of the city, so the
        // core reads as fine grain even from map scale.
        for (int depth = 1; depth <= config.maxLocalRoadDepth() + 1; depth++) {
            boolean coreOnlyRound = depth > config.maxLocalRoadDepth();
            RoadClass roadClass = depth == 1 ? RoadClass.COLLECTOR : RoadClass.LOCAL;

            for (List<GridPosition> region : RegionUtils.untypedRegions(tiles)) {
                int[] rect = RegionUtils.largestInscribedRectangle(region);
                if (rect == null) {
                    continue;
                }
                int rectW = rect[2] - rect[0] + 1;
                int rectH = rect[3] - rect[1] + 1;
                if (Math.max(rectW, rectH) < config.minBlockSizeForSplit()) {
                    continue;
                }
                // Both halves must be able to hold a lot; without this guard a
                // low minBlockSizeForSplit makes the clamp below crash
                // (min > max) on blocks big enough to qualify but too small
                // to actually split.
                int minHalf = config.minLotWidth() + 1;
                if (Math.max(rectW, rectH) < 2 * minHalf + 1) {
                    continue;
                }

                int centerX = (rect[0] + rect[2]) / 2;
                int centerY = (rect[1] + rect[3]) / 2;
                // Guaranteed split in the city core (downtown has no big
                // roadless blocks), sliding down to half the configured
                // chance at the edge - the unsplit outskirt blocks are where
                // the large parks come from.
                double d = density.at(centerX, centerY);
                // The farm belt keeps its huge arterial-to-arterial blocks:
                // fields are not street grids.
                if (d < config.farmlandDensityThreshold()) {
                    continue;
                }
                if (coreOnlyRound && d <= 0.8) {
                    continue;
                }
                double normalized = (d - config.edgeDensity()) / (1.0 - config.edgeDensity());
                double halfBase = config.blockSplitChance() * 0.5;
                double splitChance = halfBase + (1.0 - halfBase) * Math.clamp(normalized, 0.0, 1.0);
                if (random.nextDouble() > splitChance) {
                    continue;
                }

                int jitter = random.nextInt(config.arterialJitter() * 2 + 1) - config.arterialJitter();
                boolean cutVertically = rectW >= rectH;
                int split = cutVertically
                        ? Math.clamp(centerX + jitter, rect[0] + minHalf, rect[2] - minHalf)
                        : Math.clamp(centerY + jitter, rect[1] + minHalf, rect[3] - minHalf);

                cutLine(region, split, cutVertically, roadClass, roads, tiles, roadClasses);
            }
        }
    }

    /** Marks every region cell on the split line as road and records the straight runs as segments. */
    private void cutLine(List<GridPosition> region, int split, boolean vertical, RoadClass roadClass,
                         List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        List<Integer> alongs = new ArrayList<>();
        for (GridPosition cell : region) {
            if ((vertical ? cell.getX() : cell.getY()) == split) {
                alongs.add(vertical ? cell.getY() : cell.getX());
                tiles[cell.getX()][cell.getY()] = TileType.ROAD;
                if (roadClasses[cell.getX()][cell.getY()] == null
                        || roadClass.ordinal() < roadClasses[cell.getX()][cell.getY()].ordinal()) {
                    roadClasses[cell.getX()][cell.getY()] = roadClass;
                }
            }
        }
        if (alongs.isEmpty()) {
            return;
        }

        // The line may cross gaps in a concave region - record each
        // contiguous run as its own segment.
        alongs.sort(Integer::compareTo);
        int runStart = alongs.getFirst();
        int previous = runStart;
        for (int i = 1; i <= alongs.size(); i++) {
            int current = i < alongs.size() ? alongs.get(i) : Integer.MIN_VALUE;
            if (current != previous + 1) {
                roads.add(vertical
                        ? new RoadDraft(split, runStart, split, previous, roadClass)
                        : new RoadDraft(runStart, split, previous, split, roadClass));
                runStart = current;
            }
            previous = current;
        }
    }

    /**
     * Safety net: flood-fills the road network from one road tile and
     * demotes any road tile that wasn't reached. Construction should never
     * produce disconnected roads, but a "road to nowhere" granting lots
     * frontage would be a silent lie, so this guarantees it can't happen.
     */
    private void demoteDisconnected(TileType[][] tiles, RoadClass[][] roadClasses, GenerationConfig config,
                                    DensityField density, List<RoadDraft> roads) {
        int width = config.width();
        int height = config.height();

        // Label every connected road component and keep only the LARGEST.
        // Anything else (isolated bridge stubs, orphaned village streets) is
        // demoted. Never seed from a single tile chosen by scan order or by
        // density: both have kept a stray fragment and demoted the entire
        // city (seen in the wild on contact sheets) - the city is always the
        // biggest component by orders of magnitude, so size is the one
        // criterion that cannot pick wrong.
        int[][] component = new int[width][height];
        int componentCount = 0;
        int bestComponent = 0;
        int bestSize = 0;

        for (int sx = 0; sx < width; sx++) {
            for (int sy = 0; sy < height; sy++) {
                if (tiles[sx][sy] != TileType.ROAD || component[sx][sy] != 0) {
                    continue;
                }
                int label = ++componentCount;
                int size = 0;
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{sx, sy});
                component[sx][sy] = label;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    size++;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = cell[0] + d[0];
                        int ny = cell[1] + d[1];
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height
                                && tiles[nx][ny] == TileType.ROAD && component[nx][ny] == 0) {
                            component[nx][ny] = label;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                if (size > bestSize) {
                    bestSize = size;
                    bestComponent = label;
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] == TileType.ROAD && component[x][y] != bestComponent) {
                    tiles[x][y] = null;
                    roadClasses[x][y] = null;
                }
            }
        }

        // Segments whose tiles were demoted must go too, or the segment list
        // (the authoritative graph Phase B persists) would claim roads whose
        // tiles no longer exist. A straight run is internally 4-connected,
        // so a segment is always demoted whole - checking one tile suffices,
        // but check all of them to be safe.
        roads.removeIf(road -> {
            int stepX = Integer.signum(road.x1() - road.x0());
            int stepY = Integer.signum(road.y1() - road.y0());
            int x = road.x0();
            int y = road.y0();
            while (true) {
                if (tiles[x][y] != TileType.ROAD) {
                    return true;
                }
                if (x == road.x1() && y == road.y1()) {
                    return false;
                }
                x += stepX;
                y += stepY;
            }
        });
    }
}
