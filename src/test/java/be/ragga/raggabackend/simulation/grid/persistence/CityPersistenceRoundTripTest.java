package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.CityRepository;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.generation.*;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplateRepository;
import be.ragga.raggabackend.simulation.grid.persistence.mapping.GenerationResultMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end persistence check: generate a city from a fixed seed, map it onto
 * the entity graph, save it, reload it fresh from the DB, and prove the
 * reloaded city reconstructs the identical tile grid and object counts.
 *
 * Runs against an in-memory H2 (replace = ANY overrides the app's MySQL +
 * replace=none), so it needs no live database. The pipeline and mapper are
 * plain objects constructed by hand, exactly like GenerationRegressionTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CityPersistenceRoundTripTest {

    private static final long SEED = 42L;
    // Smaller than the regression size: enough to exercise lots, roads and
    // buildings while keeping the ~SIZE^2 grid-cell insert fast.
    private static final int SIZE = 200;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private BuildingTemplateRepository templateRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndReloadsIdenticalCity() {
        // Seed the catalog the mapper links placed buildings against.
        StubTemplateCatalog.standard()
                .forEach(spec -> templateRepository.save(BuildingTemplate.from(spec)));
        Map<String, BuildingTemplate> templatesByCode = templateRepository.findAll().stream()
                .collect(Collectors.toMap(BuildingTemplate::getCode, Function.identity()));

        GenerationConfig config = GenerationConfig.defaults(SIZE, SIZE);
        GenerationResult result = pipeline().generate(config, StubTemplateCatalog.standard(), SEED);

        City city = new GenerationResultMapper().toCity(result, SEED, config, templatesByCode);
        long id = cityRepository.save(city).getId();

        // Drop the persistence context so the reload is a genuine DB read.
        entityManager.flush();
        entityManager.clear();

        City reloaded = cityRepository.findById(id).orElseThrow();

        assertEquals(SEED, reloaded.getSeed(), "seed round-trips");
        assertNotNull(reloaded.getGenerationConfig(), "config JSON round-trips");
        assertEquals(SIZE, reloaded.getGenerationConfig().width(), "config width round-trips");
        assertEquals((long) SIZE * SIZE, reloaded.getGrid().size(), "every tile persisted");
        assertEquals(result.lots().size(), reloaded.getLots().size(), "lot count matches");
        assertEquals(result.roads().size(), reloaded.getRoads().size(), "road count matches");
        assertEquals(result.buildings().size(), reloaded.getBuildings().size(), "building count matches");

        // The real proof: the stored cells rebuild the exact same map.
        TileType[][] rebuilt = new TileType[SIZE][SIZE];
        for (GridCell cell : reloaded.getGrid()) {
            rebuilt[cell.getPosition().getX()][cell.getPosition().getY()] = cell.getTileType();
        }
        assertTrue(Arrays.deepEquals(result.tiles(), rebuilt),
                "reloaded grid must reconstruct the original tiles[][]");
    }

    private static GenerationPipeline pipeline() {
        return new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService());
    }
}
