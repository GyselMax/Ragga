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

    // Wandering-arterial shape: how far an arterial may stray from its base
    // line, how often it may sidestep, and how long a drift direction holds.
    private static final int MAX_DRIFT = 2;
    private static final double STEP_CHANCE = 0.25;
    private static final int DRIFT_HOLD_MIN = 4;
    private static final int DRIFT_HOLD_VARIATION = 5;

    public RoadNetwork generate(GenerationConfig config, Random random) {
        TileType[][] tiles = new TileType[config.width()][config.height()];
        RoadClass[][] roadClasses = new RoadClass[config.width()][config.height()];
        List<RoadDraft> roads = new ArrayList<>();
        DensityField density = DensityField.of(config);

        for (int baseX : arterialPositions(config.width(), config, random)) {
            wanderArterial(baseX, true, config, random, roads, tiles, roadClasses);
        }
        for (int baseY : arterialPositions(config.height(), config, random)) {
            wanderArterial(baseY, false, config, random, roads, tiles, roadClasses);
        }

        splitBlocks(config, density, random, roads, tiles, roadClasses);
        demoteDisconnected(tiles, roadClasses, config);
        return new RoadNetwork(roads, tiles, roadClasses);
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
     */
    private void wanderArterial(int basePos, boolean vertical, GenerationConfig config, Random random,
                                List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        int length = vertical ? config.height() : config.width();
        int crossExtent = vertical ? config.width() : config.height();

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

            boolean shift = driftDirection != 0
                    && random.nextDouble() < STEP_CHANCE
                    && Math.abs(pos + driftDirection - basePos) <= MAX_DRIFT
                    && pos + driftDirection >= 1
                    && pos + driftDirection <= crossExtent - 2;

            if (shift) {
                // Close the straight run up to the previous step, then mark
                // the sideways connector tile on the current step's row so
                // the road stays 4-connected through the bend.
                addSegment(pos, runStart, pos, step - 1, vertical, roads, tiles, roadClasses);
                pos += driftDirection;
                markTile(pos - driftDirection, step, vertical, tiles, roadClasses);
                runStart = step;
            }
        }
        addSegment(pos, runStart, pos, length - 1, vertical, roads, tiles, roadClasses);
    }

    /** Adds a straight arterial run; coordinates are (cross, along) pairs translated per orientation. */
    private void addSegment(int cross0, int along0, int cross1, int along1, boolean vertical,
                            List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
        if (along1 < along0) {
            return;
        }
        RoadDraft road = vertical
                ? new RoadDraft(cross0, along0, cross1, along1, RoadClass.ARTERIAL)
                : new RoadDraft(along0, cross0, along1, cross1, RoadClass.ARTERIAL);
        roads.add(road);
        for (int along = along0; along <= along1; along++) {
            markTile(cross0, along, vertical, tiles, roadClasses);
        }
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
        for (int depth = 1; depth <= config.maxLocalRoadDepth(); depth++) {
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

                int centerX = (rect[0] + rect[2]) / 2;
                int centerY = (rect[1] + rect[3]) / 2;
                // Guaranteed split in the city core (downtown has no big
                // roadless blocks), sliding down to half the configured
                // chance at the edge - the unsplit outskirt blocks are where
                // the large parks come from.
                double d = density.at(centerX, centerY);
                double normalized = (d - config.edgeDensity()) / (1.0 - config.edgeDensity());
                double halfBase = config.blockSplitChance() * 0.5;
                double splitChance = halfBase + (1.0 - halfBase) * Math.clamp(normalized, 0.0, 1.0);
                if (random.nextDouble() > splitChance) {
                    continue;
                }

                // Halves must stay wide enough to hold at least one strip of lots.
                int minHalf = config.minLotWidth() + 1;
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
    private void demoteDisconnected(TileType[][] tiles, RoadClass[][] roadClasses, GenerationConfig config) {
        int width = config.width();
        int height = config.height();
        boolean[][] reached = new boolean[width][height];

        Deque<int[]> queue = new ArrayDeque<>();
        outer:
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] == TileType.ROAD) {
                    queue.add(new int[]{x, y});
                    reached[x][y] = true;
                    break outer;
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cell[0] + d[0];
                int ny = cell[1] + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height
                        && tiles[nx][ny] == TileType.ROAD && !reached[nx][ny]) {
                    reached[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] == TileType.ROAD && !reached[x][y]) {
                    tiles[x][y] = null;
                    roadClasses[x][y] = null;
                }
            }
        }
    }
}
