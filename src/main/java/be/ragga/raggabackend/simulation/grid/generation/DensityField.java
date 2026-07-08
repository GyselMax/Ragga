package be.ragga.raggabackend.simulation.grid.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Density surface driving every stage's probabilities (vacancy, road
 * splitting, zoning, building size) instead of flat constants: high in city
 * cores, falling off to edgeDensity countryside.
 *
 * The field is a set of {@link Core} blobs; the density at a tile is the
 * MAX over all cores (so overlapping influence takes the denser value, and
 * the whole thing floors at edgeDensity). One core with peak 1.0 reproduces
 * the original single radial-core behavior exactly.
 *
 * - cores[0] is the MAIN city: off-center-jittered per seed, peak 1.0, and
 *   the only core that gets the river lobe (withRiver) - real cities grow
 *   ALONG their river, so the main core stretches into an elongated blob.
 * - Later cores are secondary settlements: satellites (added in of(), before
 *   terrain so forest carving respects them) and hamlets (added by
 *   withSettlements after terrain). Smaller radius, lower peak.
 *
 * The pipeline builds ONE field and upgrades it in place (withRiver, then
 * withSettlements); every stage shares that instance, so the jittered
 * centers and lobes never disagree.
 */
public record DensityField(int width, int height, List<Core> cores,
                           double coreRadiusFraction, double edgeDensity,
                           boolean riverVertical, int[] riverCenterline) {

    /**
     * One density blob. {@code radius} is the falloff normalization length in
     * tiles (density hits its floor around here); {@code peak} in (0,1] scales
     * the blob's height so satellites and hamlets top out below downtown.
     */
    public record Core(double x, double y, double radius, double peak) {
    }

    // Shape of the main core's river lobe: cross tightness squeezes it
    // against the banks; along the flow the distance term starts gentle
    // (stretching the core ~1.6x up- and downstream) but grows quadratically,
    // so the lobe closes before the map border instead of running the whole
    // river.
    private static final double RIVER_CROSS_TIGHTNESS = 1.6;
    private static final double RIVER_ALONG_LINEAR = 0.45;
    private static final double RIVER_ALONG_QUADRATIC = 0.55;

    /**
     * Builds the field: a jittered main core plus any satellite cores
     * (config.cityCount - 1), rejection-placed so their urban extents never
     * touch each other or the main city. No river lobe yet - that is added by
     * withRiver once the terrain exists.
     */
    public static DensityField of(GenerationConfig config, Random random) {
        double jitter = config.coreCenterJitter();
        double centerX = config.width() * (0.5 + (random.nextDouble() * 2 - 1) * jitter);
        double centerY = config.height() * (0.5 + (random.nextDouble() * 2 - 1) * jitter);
        double edgeDistance = Math.min(Math.min(centerX, config.width() - centerX),
                Math.min(centerY, config.height() - centerY));
        double mainRadius = config.maxCityRadius() > 0
                ? Math.min(edgeDistance, config.maxCityRadius())
                : edgeDistance;

        List<Core> cores = new ArrayList<>();
        cores.add(new Core(centerX, centerY, mainRadius, 1.0));
        placeSatellites(cores, config, mainRadius, random);

        return new DensityField(config.width(), config.height(), cores,
                config.coreRadiusFraction(), config.edgeDensity(), false, null);
    }

    /** Rejection-samples secondary city centers whose urban extents stay clear of every existing core. */
    private static void placeSatellites(List<Core> cores, GenerationConfig config, double mainRadius, Random random) {
        int satellites = config.cityCount() - 1;
        double peak = (config.satellitePeakDensity() - config.edgeDensity())
                / (1.0 - config.edgeDensity());
        for (int i = 0; i < satellites; i++) {
            double radius = mainRadius * (config.satelliteMinScale()
                    + random.nextDouble() * (config.satelliteMaxScale() - config.satelliteMinScale()));
            for (int attempt = 0; attempt < 30; attempt++) {
                double cx = radius + random.nextDouble() * (config.width() - 2 * radius);
                double cy = radius + random.nextDouble() * (config.height() - 2 * radius);
                if (clearOfAll(cores, cx, cy, radius)) {
                    cores.add(new Core(cx, cy, radius, peak));
                    break;
                }
            }
            // Attempts exhausted: silently place fewer satellites, never fail.
        }
    }

    /** True when a blob of the given radius keeps a farm-gap clearance from every existing core. */
    private static boolean clearOfAll(List<Core> cores, double x, double y, double radius) {
        for (Core c : cores) {
            if (Math.hypot(x - c.x(), y - c.y()) < 1.1 * (c.radius() + radius)) {
                return false;
            }
        }
        return true;
    }

    /** The same field with the main core's river lobe added - call once the terrain exists. */
    public DensityField withRiver(TerrainResult terrain) {
        if (!terrain.hasRiver()) {
            return this;
        }
        return new DensityField(width, height, cores, coreRadiusFraction, edgeDensity,
                terrain.riverVertical(), terrain.riverCenterline());
    }

    /** The same field with extra settlement cores (hamlets) appended - call after terrain/planning. */
    public DensityField withSettlements(List<Core> extraCores) {
        if (extraCores.isEmpty()) {
            return this;
        }
        List<Core> merged = new ArrayList<>(cores);
        merged.addAll(extraCores);
        return new DensityField(width, height, merged, coreRadiusFraction, edgeDensity,
                riverVertical, riverCenterline);
    }

    /** The main city core (cores[0]): its center and radius anchor the river and set-piece placement. */
    public Core mainCore() {
        return cores.get(0);
    }

    public double centerX() {
        return mainCore().x();
    }

    public double centerY() {
        return mainCore().y();
    }

    /**
     * The main core's falloff radius in tiles - the length every "fraction of
     * the city" quantity is measured against (core radius, farmland boundary,
     * river lobe extent, river offset).
     */
    public double normalizationRadius() {
        return mainCore().radius();
    }

    /** Density at a tile, in [edgeDensity, 1]: the max over every core's contribution. */
    public double at(int x, int y) {
        double px = x + 0.5;
        double py = y + 0.5;
        double best = edgeDensity;

        for (int i = 0; i < cores.size(); i++) {
            Core c = cores.get(i);
            // Secondary cores have no river lobe, so their influence is
            // strictly inside the radius bbox - skip tiles outside it cheaply
            // (the main core is never skipped; its lobe reaches past the box).
            if (i > 0 && (Math.abs(px - c.x()) > c.radius() || Math.abs(py - c.y()) > c.radius())) {
                continue;
            }

            double distance = Math.hypot(px - c.x(), py - c.y()) / c.radius();
            if (i == 0 && riverCenterline != null) {
                distance = Math.min(distance, riverLobeDistance(x, y, c));
            }

            double profile = distance <= coreRadiusFraction
                    ? 1.0
                    : Math.max(0.0, 1.0 - (distance - coreRadiusFraction) / (1.0 - coreRadiusFraction));
            best = Math.max(best, edgeDensity + c.peak() * profile * (1.0 - edgeDensity));
        }
        return best;
    }

    /** Normalized distance to the meandering river centerline: squeezed across the flow, stretched along it. */
    private double riverLobeDistance(int x, int y, Core c) {
        int along = Math.clamp(riverVertical ? y : x, 0, riverCenterline.length - 1);
        int cross = riverVertical ? x : y;
        double coreAlong = riverVertical ? c.y() : c.x();
        double crossDistance = Math.abs(cross - riverCenterline[along]) / c.radius();
        double alongDistance = Math.abs(along + 0.5 - coreAlong) / c.radius();
        double alongTerm = RIVER_ALONG_LINEAR * alongDistance
                + RIVER_ALONG_QUADRATIC * alongDistance * alongDistance;
        return Math.hypot(crossDistance * RIVER_CROSS_TIGHTNESS, alongTerm);
    }
}
