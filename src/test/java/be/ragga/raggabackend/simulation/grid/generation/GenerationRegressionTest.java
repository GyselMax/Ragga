package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.ZoneType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-seed regression tests for the generation pipeline (see design/design-2.0.md,
 * testing requirement). Generation is fully deterministic per seed, so one
 * known seed's output statistics are locked in as exact expected values.
 *
 * IF THIS TEST FAILS after a deliberate generation change: that's the test
 * doing its job - it caught that generation output changed. Re-derive the
 * golden numbers (run the pipeline at 400x400 seed 42, e.g. via
 * GridVisualizer with those values, and read the sanity-check summary) and
 * update the GOLDEN_* constants in the same commit. If you did NOT intend
 * to change generation output, you broke something - investigate before
 * touching the constants.
 *
 * No Spring context: the pipeline components are stateless and constructed
 * by hand, exactly like GridVisualizer does.
 */
class GenerationRegressionTest {

    private static final long SEED = 42L;
    private static final int SIZE = 400;

    // Golden numbers for defaults(400, 400), seed 42.
    // Last updated: re-derived after the multi-core defaults (maxCityRadius,
    // cityCount/satellites/hamlets, arterialSpacing 55) and the enlarged
    // template catalog (highrise/mansions/megamall/megafactory) landed - both
    // deliberate generation changes that shifted every stat.
    private static final int GOLDEN_LOTS = 1950;
    private static final int GOLDEN_RESIDENTIAL = 952;
    private static final int GOLDEN_COMMERCIAL = 640;
    private static final int GOLDEN_INDUSTRIAL = 265;
    private static final int GOLDEN_FARMLAND = 93;
    private static final int GOLDEN_VACANT = 159;
    private static final int GOLDEN_PUBLIC_SITES = 98;
    private static final int GOLDEN_BUILDINGS = 1791;
    private static final int GOLDEN_ROAD_SEGMENTS = 803;
    private static final int GOLDEN_ROAD_TILES = 10071;
    private static final int GOLDEN_PARK_TILES = 50611;
    private static final int GOLDEN_WATER_TILES = 4591;
    private static final int GOLDEN_FOREST_TILES = 41015;

    private static GenerationResult result;

    @BeforeAll
    static void generateOnce() {
        result = generate();
    }

    private static GenerationResult generate() {
        GenerationPipeline pipeline = new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService());
        return pipeline.generate(GenerationConfig.defaults(SIZE, SIZE), StubTemplateCatalog.standard(), SEED);
    }

    @Test
    void sanityCheckInvariantsHold() {
        for (Map.Entry<String, Long> check : GenerationSanityCheck.violations(result).entrySet()) {
            assertEquals(0L, check.getValue(),
                    "invariant violated: " + check.getKey());
        }
    }

    @Test
    void goldenSeedStatsUnchanged() {
        assertEquals(GOLDEN_LOTS, result.lots().size(), "total lots");
        assertEquals(GOLDEN_RESIDENTIAL, zoneCount(ZoneType.RESIDENTIAL), "residential lots");
        assertEquals(GOLDEN_COMMERCIAL, zoneCount(ZoneType.COMMERCIAL), "commercial lots");
        assertEquals(GOLDEN_INDUSTRIAL, zoneCount(ZoneType.INDUSTRIAL), "industrial lots");
        assertEquals(GOLDEN_FARMLAND, zoneCount(ZoneType.FARMLAND), "farmland lots");
        assertEquals(0, zoneCount(ZoneType.UNDER_CONSTRUCTION), "generator must never emit UNDER_CONSTRUCTION");
        assertEquals(GOLDEN_VACANT, result.lots().stream().filter(LotDraft::isVacant).count(), "vacant lots");
        assertEquals(GOLDEN_PUBLIC_SITES, result.lots().stream().filter(LotDraft::isPublicSite).count(), "public sites");
        assertEquals(GOLDEN_BUILDINGS, result.buildings().size(), "buildings");
        assertEquals(GOLDEN_ROAD_SEGMENTS, result.roads().size(), "road segments");
        assertEquals(GOLDEN_ROAD_TILES, tileCount(TileType.ROAD), "road tiles");
        assertEquals(GOLDEN_PARK_TILES, tileCount(TileType.PARK), "park tiles");
        assertEquals(GOLDEN_WATER_TILES, tileCount(TileType.WATER), "water tiles");
        assertEquals(GOLDEN_FOREST_TILES, tileCount(TileType.FOREST), "forest tiles");
        assertEquals(0, tileCount(TileType.UNUSED), "generator should emit no UNUSED tiles");
    }

    @Test
    void sameSeedIsFullyDeterministic() {
        GenerationResult second = generate();
        assertTrue(Arrays.deepEquals(result.tiles(), second.tiles()),
                "two runs with the same seed and config must produce identical tile grids");
        assertEquals(result.roads().size(), second.roads().size(), "road segment count must be deterministic");
        assertEquals(result.lots().size(), second.lots().size(), "lot count must be deterministic");
        assertEquals(result.buildings().size(), second.buildings().size(), "building count must be deterministic");
    }

    private long zoneCount(ZoneType zone) {
        return result.lots().stream().filter(lot -> lot.getZone() == zone).count();
    }

    private int tileCount(TileType type) {
        int count = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (result.tiles()[x][y] == type) {
                    count++;
                }
            }
        }
        return count;
    }
}
