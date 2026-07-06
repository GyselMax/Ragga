package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;

import java.util.List;

/**
 * Everything the pipeline produced for one city, fully in memory. The
 * visualizer renders this directly; Phase B maps it onto JPA entities.
 */
public record GenerationResult(
        GenerationConfig config,
        TileType[][] tiles,
        RoadClass[][] roadClasses,
        List<RoadDraft> roads,
        List<LotDraft> lots,
        List<BuildingDraft> buildings
) {
}
