package be.ragga.raggabackend.simulation.grid.generation;

/**
 * Radial density: 1.0 inside the city core, falling off linearly to
 * edgeDensity at the map border - the classic "dense downtown, looser
 * outskirts" shape of real cities. Stages use this to modulate their
 * probabilities (vacancy, road splitting, building size) instead of applying
 * flat constants everywhere.
 */
public record DensityField(int width, int height, double coreRadiusFraction, double edgeDensity) {

    public static DensityField of(GenerationConfig config) {
        return new DensityField(config.width(), config.height(),
                config.coreRadiusFraction(), config.edgeDensity());
    }

    /** Density at a tile, in [edgeDensity, 1]. */
    public double at(int x, int y) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        // Normalized against the distance from center to the nearest map
        // edge, so the gradient reaches its minimum on the border.
        double maxDistance = Math.min(cx, cy);
        double distance = Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / maxDistance;

        if (distance <= coreRadiusFraction) {
            return 1.0;
        }
        double fade = (distance - coreRadiusFraction) / (1.0 - coreRadiusFraction);
        return Math.max(edgeDensity, 1.0 - fade * (1.0 - edgeDensity));
    }
}
