package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.TileType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runs the whole generation, in the user-chosen order:
 * terrain -> roads -> blocks -> lots -> parks -> zoning -> buildings.
 *
 * Fully in-memory and deterministic per seed. Phase B's persistence service
 * calls this and maps the result onto JPA entities; GridVisualizer calls it
 * directly with no Spring context.
 */
@Component
public class GenerationPipeline {

    private final TerrainGenerator terrainGenerator;
    private final SettlementPlanner settlementPlanner;
    private final RoadNetworkGenerator roadNetworkGenerator;
    private final BlockSubdivisionService blockSubdivisionService;
    private final LotSubdivisionService lotSubdivisionService;
    private final ZoneAssignmentService zoneAssignmentService;
    private final BuildingPlacementService buildingPlacementService;

    public GenerationPipeline(TerrainGenerator terrainGenerator,
                              SettlementPlanner settlementPlanner,
                              RoadNetworkGenerator roadNetworkGenerator,
                              BlockSubdivisionService blockSubdivisionService,
                              LotSubdivisionService lotSubdivisionService,
                              ZoneAssignmentService zoneAssignmentService,
                              BuildingPlacementService buildingPlacementService) {
        this.terrainGenerator = terrainGenerator;
        this.settlementPlanner = settlementPlanner;
        this.roadNetworkGenerator = roadNetworkGenerator;
        this.blockSubdivisionService = blockSubdivisionService;
        this.lotSubdivisionService = lotSubdivisionService;
        this.zoneAssignmentService = zoneAssignmentService;
        this.buildingPlacementService = buildingPlacementService;
    }

    public GenerationResult generate(GenerationConfig config, List<TemplateSpec> catalog, long seed) {
        Random random = new Random(seed);
        // Built once and threaded through every stage: the core center is
        // jittered per seed, so independent DensityField.of calls would
        // disagree on where downtown is.
        DensityField density = DensityField.of(config, random);

        TerrainResult terrain = terrainGenerator.generate(config, density, random);
        // Once the river exists, the core stretches along it: the upgraded
        // field is what every stage from roads onward must see.
        density = density.withRiver(terrain);
        // Scatter hamlets and plan the countryside road graph, then fold the
        // village bumps into the field so the pipeline builds each one.
        SettlementPlan settlements = settlementPlanner.plan(config, density, terrain, random);
        density = density.withSettlements(settlements.hamletCores());
        RoadNetwork network = roadNetworkGenerator.generate(config, density, terrain, settlements, random);
        TileType[][] tiles = network.tiles();

        List<Block> blocks = blockSubdivisionService.findBlocks(tiles, config);

        List<LotDraft> lots = new ArrayList<>();
        List<GridPosition> parkCells = new ArrayList<>();
        List<GridPosition> unusedCells = new ArrayList<>();
        for (Block block : blocks) {
            LotSubdivisionService.SubdivisionResult result =
                    lotSubdivisionService.subdivide(block, tiles, network.roadClasses(), config, density, random);
            lots.addAll(result.lots());
            parkCells.addAll(result.parkCells());
            unusedCells.addAll(result.unusedCells());
        }

        for (LotDraft lot : lots) {
            for (int x = lot.getX(); x < lot.getX() + lot.getWidth(); x++) {
                for (int y = lot.getY(); y < lot.getY() + lot.getDepth(); y++) {
                    tiles[x][y] = TileType.LOT;
                }
            }
        }
        for (GridPosition cell : parkCells) {
            tiles[cell.getX()][cell.getY()] = TileType.PARK;
        }
        // Degenerate subdivision leftovers read better as green pockets than
        // as dead gray slivers.
        for (GridPosition cell : unusedCells) {
            tiles[cell.getX()][cell.getY()] = TileType.PARK;
        }
        // Safety net: no cell may stay untyped.
        for (int x = 0; x < config.width(); x++) {
            for (int y = 0; y < config.height(); y++) {
                if (tiles[x][y] == null) {
                    tiles[x][y] = TileType.UNUSED;
                }
            }
        }

        zoneAssignmentService.assign(lots, config, density, random);
        List<BuildingDraft> buildings = buildingPlacementService.place(lots, catalog, tiles, random);

        return new GenerationResult(config, tiles, network.roadClasses(), network.roads(), lots, buildings);
    }
}
