package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;

import java.util.List;

/**
 * Everything the pipeline produced for one city, fully in memory. The
 * visualizer renders this directly; Phase B maps it onto JPA entities.
 *
 * {@code densityField} is the multi-core centrality surface every generation
 * stage was driven by; it is carried through here (not recomputed) so the
 * post-generation economy pass can read land-value / centrality per location
 * off the exact same jittered field the city was built from.
 */
public record GenerationResult(
        GenerationConfig config,
        TileType[][] tiles,
        RoadClass[][] roadClasses,
        List<RoadDraft> roads,
        List<LotDraft> lots,
        List<BuildingDraft> buildings,
        DensityField densityField
) {
}
