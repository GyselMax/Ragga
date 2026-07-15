package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;

import java.time.Instant;

/**
 * Lightweight view of a stored city for the API - metadata plus object counts,
 * without serializing the ~250k-cell grid. A full grid export, if ever needed,
 * belongs on its own endpoint.
 */
public record CitySummary(
        long id,
        String name,
        long seed,
        Instant generatedAt,
        int width,
        int height,
        long gridCellCount,
        int lotCount,
        int roadCount,
        int buildingCount,
        // NOTE: accurate only while City.simulatedBuildings is residential-only
        // (see GenerationResultMapper.mapBuilding). Add a zone/type filter here
        // once commercial/industrial bridging lands, or this will silently
        // start counting non-residential buildings too.
        int simulatedResidentialCount
) {

    public static CitySummary of(City city) {
        GenerationConfig config = city.getGenerationConfig();
        int width = config.width();
        int height = config.height();
        // The grid is a full raster by construction, so its cell count equals
        // width*height - computed rather than loaded to avoid pulling every
        // GridCell row just to size the collection.
        long gridCellCount = (long) width * height;
        return new CitySummary(
                city.getId(),
                city.getName(),
                city.getSeed(),
                city.getGeneratedAt(),
                width,
                height,
                gridCellCount,
                city.getLots().size(),
                city.getRoads().size(),
                city.getBuildings().size(),
                city.getEconomicBuildings().size());
    }
}
