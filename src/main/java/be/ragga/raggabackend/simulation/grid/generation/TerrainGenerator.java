package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage 0: natural terrain, carved before any road exists.
 *
 * Currently one feature: a meandering river crossing the whole map. It
 * reuses the same random-walk-with-momentum idea as the wandering arterials,
 * but with far longer drift holds and a much wider drift bound, so it bends
 * in big lazy curves instead of a road-like wiggle. The width breathes a
 * little per step so the banks aren't ruler-straight.
 *
 * The river's base line runs through the city core (cities grow around
 * their river), offset by up to half the core radius so it sometimes cuts
 * downtown in two and sometimes just grazes it.
 *
 * Every WATER tile gets a 1-tile PARK bank. Because all of this is
 * pre-seeded into the tile grid the road generator draws into, the rest of
 * the pipeline needs no water handling: block flood-fill, road splitting and
 * lot subdivision only ever operate on null tiles. Only arterials overwrite
 * water - those tiles are the bridges.
 *
 * Stateless: config and randomness are passed in per call.
 */
@Component
public class TerrainGenerator {

    // River meander rhythm: shifts often (high step chance) but holds a
    // drift direction for a long time - big smooth curves, not zigzag.
    private static final double RIVER_STEP_CHANCE = 0.5;
    private static final int RIVER_HOLD_MIN = 30;
    private static final int RIVER_HOLD_VARIATION = 50;

    public TerrainResult generate(GenerationConfig config, DensityField density, Random random) {
        TileType[][] tiles = new TileType[config.width()][config.height()];
        if (config.forestsEnabled()) {
            carveForests(tiles, config, density, random);
        }
        if (!config.riverEnabled()) {
            return TerrainResult.noRiver(tiles);
        }

        boolean vertical = random.nextBoolean();
        int length = vertical ? config.height() : config.width();
        int crossExtent = vertical ? config.width() : config.height();

        // Widest the carve brush can get (base half-width + 2 of breathing).
        int halfMax = config.riverWidth() / 2 + 2;
        if (crossExtent < 4 * (halfMax + 2)) {
            // Map too narrow for a river plus usable banks on both sides -
            // tiny test maps just stay dry.
            return TerrainResult.noRiver(tiles);
        }

        // Base line through the core, offset by up to half the core radius:
        // sometimes the river splits downtown, sometimes it grazes it. The
        // radius comes from the density field so it agrees with the falloff
        // even when maxCityRadius caps the city on big maps.
        double coreCross = vertical ? density.centerX() : density.centerY();
        double coreRadius = config.coreRadiusFraction() * density.normalizationRadius();
        int base = (int) Math.round(coreCross + (random.nextDouble() * 2 - 1) * coreRadius * 0.5);
        base = Math.clamp(base, halfMax + 2, crossExtent - halfMax - 3);

        int[] centerline = walkCenterline(base, length, crossExtent, halfMax, config, random);
        carve(tiles, centerline, vertical, config, random);
        growBanks(tiles);
        return new TerrainResult(tiles, vertical, centerline);
    }

    /** Random walk with momentum along the flow axis, bounded around the base line. */
    private int[] walkCenterline(int base, int length, int crossExtent, int halfMax,
                                 GenerationConfig config, Random random) {
        int[] centerline = new int[length];
        int pos = base;
        int driftDirection = 0;
        int driftHold = 0;

        for (int step = 0; step < length; step++) {
            if (driftHold == 0) {
                // Same heading logic as the arterials: random, biased back
                // toward the base line the further the river has strayed.
                int pull = Integer.signum(base - pos);
                driftDirection = switch (random.nextInt(3)) {
                    case 0 -> pull == 0 ? (random.nextBoolean() ? 1 : -1) : pull;
                    case 1 -> 0;
                    default -> random.nextBoolean() ? 1 : -1;
                };
                driftHold = RIVER_HOLD_MIN + random.nextInt(RIVER_HOLD_VARIATION);
            }
            driftHold--;

            int next = pos + driftDirection;
            if (driftDirection != 0
                    && random.nextDouble() < RIVER_STEP_CHANCE
                    && Math.abs(next - base) <= config.riverMaxDrift()
                    && next >= halfMax + 2
                    && next <= crossExtent - halfMax - 3) {
                pos = next;
            }
            centerline[step] = pos;
        }
        return centerline;
    }

    /** Stamps the water brush along the centerline; the half-width breathes ±2 tiles. */
    private void carve(TileType[][] tiles, int[] centerline, boolean vertical,
                       GenerationConfig config, Random random) {
        int baseHalf = Math.max(1, config.riverWidth() / 2);
        int half = baseHalf;
        for (int step = 0; step < centerline.length; step++) {
            if (random.nextDouble() < 0.15) {
                half = Math.clamp(half + (random.nextBoolean() ? 1 : -1),
                        Math.max(1, baseHalf - 2), baseHalf + 2);
            }
            for (int cross = centerline[step] - half; cross <= centerline[step] + half; cross++) {
                int x = vertical ? cross : step;
                int y = vertical ? step : cross;
                if (x >= 0 && x < tiles.length && y >= 0 && y < tiles[0].length) {
                    tiles[x][y] = TileType.WATER;
                }
            }
        }
    }

    // Forests appear where smooth value noise exceeds a density-raised
    // cutoff: plenty of woodland at the rim and in the corners, none
    // downtown - where real forests survive urbanization.
    private static final int FOREST_NOISE_CELL = 56;
    private static final int FOREST_DETAIL_CELL = 20;

    private void carveForests(TileType[][] tiles, GenerationConfig config,
                              DensityField density, Random random) {
        int width = tiles.length;
        int height = tiles[0].length;
        double[][] coarse = noiseLattice(width, height, FOREST_NOISE_CELL, random);
        double[][] detail = noiseLattice(width, height, FOREST_DETAIL_CELL, random);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double noise = 0.75 * sample(coarse, x, y, FOREST_NOISE_CELL)
                        + 0.25 * sample(detail, x, y, FOREST_DETAIL_CELL);
                double d = density.at(x, y);
                // Base cutoff comes from forestDensity (higher = lower cutoff =
                // more forest); the density term still raises it toward the core
                // so downtown stays clear. forestDensity 0.42 => base 0.58, the
                // old fixed value.
                double cutoff = (1.0 - config.forestDensity()) + 1.2 * (d - config.edgeDensity());
                if (noise > cutoff) {
                    tiles[x][y] = TileType.FOREST;
                }
            }
        }
    }

    /** Random lattice for value noise, one node per cell plus a border. */
    private double[][] noiseLattice(int width, int height, int cell, Random random) {
        double[][] lattice = new double[width / cell + 2][height / cell + 2];
        for (double[] row : lattice) {
            for (int i = 0; i < row.length; i++) {
                row[i] = random.nextDouble();
            }
        }
        return lattice;
    }

    /** Bilinear value-noise sample with smoothstep easing between lattice nodes. */
    private double sample(double[][] lattice, int x, int y, int cell) {
        int gx = x / cell;
        int gy = y / cell;
        double fx = smoothstep((x % cell) / (double) cell);
        double fy = smoothstep((y % cell) / (double) cell);
        double top = lattice[gx][gy] * (1 - fx) + lattice[gx + 1][gy] * fx;
        double bottom = lattice[gx][gy + 1] * (1 - fx) + lattice[gx + 1][gy + 1] * fx;
        return top * (1 - fy) + bottom * fy;
    }

    private double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Every untyped tile touching water (8-neighborhood) becomes a PARK
     * bank: a green ribbon tracing the river, and a guarantee that no lot
     * ever borders the water directly.
     */
    private void growBanks(TileType[][] tiles) {
        int width = tiles.length;
        int height = tiles[0].length;
        List<int[]> banks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] != null) {
                    continue;
                }
                neighbors:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height
                                && tiles[nx][ny] == TileType.WATER) {
                            banks.add(new int[]{x, y});
                            break neighbors;
                        }
                    }
                }
            }
        }
        for (int[] bank : banks) {
            tiles[bank[0]][bank[1]] = TileType.PARK;
        }
    }
}
