package be.ragga.raggabackend.simulation.grid.persistence.mapping;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.building.Building;
import be.ragga.raggabackend.simulation.building.simulated.SimulatedHighRise;
import be.ragga.raggabackend.simulation.building.simulated.SimulatedLowRise;
import be.ragga.raggabackend.simulation.building.simulated.SimulatedResidential;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.ZoneType;
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
        List<PlacedBuilding> placedBuildings = new ArrayList<>();
        List<Building> simulatedBuildings = new ArrayList<>();
        for (LotDraft draft : result.lots()) {
            Lot lot = new Lot(
                    new GridPosition(draft.getX(), draft.getY()),
                    draft.getWidth(), draft.getDepth(),
                    draft.getZone(), draft.isVacant(), draft.isPublicSite());
            lots.add(lot);

            BuildingDraft buildingDraft = draft.getBuilding();
            if (buildingDraft != null) {
                PlacedBuilding placedBuilding = mapBuilding(buildingDraft, lot, templatesByCode, simulatedBuildings);
                lot.setBuilding(placedBuilding);
                placedBuildings.add(placedBuilding);
            }
        }
        city.setLots(lots);
        city.setBuildings(placedBuildings);
        city.setSimulatedBuildings(simulatedBuildings);

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
                                       Map<String, BuildingTemplate> templatesByCode,
                                       List<Building> simulatedBuildings) {
        BuildingTemplate template = templatesByCode.get(draft.template().code());
        if (template == null) {
            throw new IllegalStateException(
                    "no persisted BuildingTemplate for code " + draft.template().code()
                            + " - is the catalog seeded?");
        }

        // Only residential placements are bridged to the economic side today;
        // template().zone() (not the lot's own zone) is the right discriminator,
        // since a residential-zoned lot flagged as a public site gets a public
        // template (zone == null) instead. Commercial/industrial/farm/public
        // placements stay physical-only for now (see design/design-2.0.md).
        Building building = null;
        if (draft.template().zone() == ZoneType.RESIDENTIAL) {
            building = residentialFor(draft);
            simulatedBuildings.add(building);
        }

        return new PlacedBuilding(
                new GridPosition(draft.x(), draft.y()),
                draft.sizeX(), draft.sizeY(), draft.facing(),
                template, lot, building);
    }

    // The blueprint's floors decide the concrete subtype: at or above the
    // high-rise cutoff (see SimulatedResidential.HIGH_RISE_MIN_FLOORS) it's a
    // tower, below it a low-rise. The effective (possibly rotated) footprint
    // is passed through; floors never rotate.
    private SimulatedResidential residentialFor(BuildingDraft draft) {
        int floors = draft.template().floors();
        int capacity = draft.template().householdCapacity();
        return floors >= SimulatedResidential.HIGH_RISE_MIN_FLOORS
                ? new SimulatedHighRise(draft.sizeX(), draft.sizeY(), floors, capacity)
                : new SimulatedLowRise(draft.sizeX(), draft.sizeY(), floors, capacity);
    }
}
