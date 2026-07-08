package be.ragga.raggabackend.simulation.grid.persistence.mapping;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.generation.BuildingDraft;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.LotDraft;
import be.ragga.raggabackend.simulation.grid.generation.RoadDraft;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.entity.Lot;
import be.ragga.raggabackend.simulation.grid.persistence.entity.PlacedBuilding;
import be.ragga.raggabackend.simulation.grid.persistence.entity.RoadSegment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure translation from the generator's in-memory {@link GenerationResult} to a
 * persisted {@link City} entity graph. Holds no state and touches no
 * repository - the caller supplies already-loaded {@link BuildingTemplate}
 * rows so this stays a straight object-to-object map.
 *
 * The full grid ({@code tiles[][]}) is stored cell-by-cell; {@code roadClasses[][]}
 * is not stored, since it is rebuildable from the road segments on load.
 */
@Component
public class GenerationResultMapper {

    /**
     * @param templatesByCode every catalog template keyed by its code, used to
     *                        link each placed building back to its template row
     */
    public City toCity(GenerationResult result, long seed, GenerationConfig config,
                       Map<String, BuildingTemplate> templatesByCode) {
        City city = new City();
        city.setSeed(seed);
        city.setGenerationConfig(config);
        city.setGeneratedAt(Instant.now());

        city.setGrid(mapGrid(result));
        city.setRoads(mapRoads(result.roads()));

        // Lots and buildings are mapped together: every BuildingDraft is
        // attached to its owning LotDraft during generation (public buildings
        // included, on publicSite lots), so walking lots covers every building
        // and gives each PlacedBuilding its lot link for free.
        List<Lot> lots = new ArrayList<>();
        List<PlacedBuilding> buildings = new ArrayList<>();
        for (LotDraft draft : result.lots()) {
            Lot lot = new Lot(
                    new GridPosition(draft.getX(), draft.getY()),
                    draft.getWidth(), draft.getDepth(),
                    draft.getZone(), draft.isVacant(), draft.isPublicSite());
            lots.add(lot);

            BuildingDraft buildingDraft = draft.getBuilding();
            if (buildingDraft != null) {
                PlacedBuilding building = mapBuilding(buildingDraft, lot, templatesByCode);
                lot.setBuilding(building);
                buildings.add(building);
            }
        }
        city.setLots(lots);
        city.setBuildings(buildings);

        return city;
    }

    private List<GridCell> mapGrid(GenerationResult result) {
        var tiles = result.tiles();
        int width = result.config().width();
        int height = result.config().height();
        List<GridCell> cells = new ArrayList<>(width * height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells.add(new GridCell(new GridPosition(x, y), tiles[x][y]));
            }
        }
        return cells;
    }

    private List<RoadSegment> mapRoads(List<RoadDraft> roads) {
        List<RoadSegment> segments = new ArrayList<>(roads.size());
        for (RoadDraft road : roads) {
            segments.add(new RoadSegment(
                    new GridPosition(road.x0(), road.y0()),
                    new GridPosition(road.x1(), road.y1()),
                    road.roadClass()));
        }
        return segments;
    }

    private PlacedBuilding mapBuilding(BuildingDraft draft, Lot lot,
                                       Map<String, BuildingTemplate> templatesByCode) {
        BuildingTemplate template = templatesByCode.get(draft.template().code());
        if (template == null) {
            throw new IllegalStateException(
                    "no persisted BuildingTemplate for code " + draft.template().code()
                            + " - is the catalog seeded?");
        }
        return new PlacedBuilding(
                new GridPosition(draft.x(), draft.y()),
                draft.sizeX(), draft.sizeY(), draft.facing(),
                template, lot);
    }
}
