package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.building.AgriculturalBuilding;
import be.ragga.raggabackend.simulation.building.CommercialBuilding;
import be.ragga.raggabackend.simulation.building.HighRiseResidential;
import be.ragga.raggabackend.simulation.building.IndustrialBuilding;
import be.ragga.raggabackend.simulation.building.LowRiseResidential;
import be.ragga.raggabackend.simulation.building.ResidentialBuilding;
import be.ragga.raggabackend.simulation.economy.BusinessEconomics;
import be.ragga.raggabackend.simulation.economy.BusinessEconomyConfig;
import be.ragga.raggabackend.simulation.economy.BusinessMarketService;
import be.ragga.raggabackend.simulation.economy.EconomyConfig;
import be.ragga.raggabackend.simulation.economy.HousingEconomics;
import be.ragga.raggabackend.simulation.economy.HousingMarketService;
import be.ragga.raggabackend.simulation.economy.PopulationService;
import be.ragga.raggabackend.simulation.grid.CityRepository;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.generation.*;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplateRepository;
import be.ragga.raggabackend.simulation.grid.persistence.entity.PlacedBuilding;
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
        // Run the same market/population pass generateAndSave does, so the saved
        // city carries cleared values, rents, tenures, owners and households.
        EconomyConfig economyConfig = EconomyConfig.defaults();
        PopulationService population = new PopulationService(economyConfig,
                new HousingMarketService(economyConfig, new HousingEconomics(economyConfig)));
        population.populate(city, result, SEED);
        BusinessEconomyConfig businessConfig = BusinessEconomyConfig.defaults();
        BusinessMarketService businessMarket = new BusinessMarketService(
                businessConfig, new BusinessEconomics(businessConfig));
        businessMarket.clear(city, result, SEED, null);

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

        // Every zoned placement is bridged to an economic Building of the right
        // function; only public-use placements (zone == null) stay physical-only.
        // Residential is fully checked (capacity/bedrooms/subtype vs the high-rise
        // cutoff); the three business sectors must bridge to their sector type and
        // be priced by the business market.
        int bridged = 0;
        for (PlacedBuilding placed : reloaded.getBuildings()) {
            ZoneType zone = placed.getTemplate().getZone();
            var building = placed.getBuilding();
            if (zone == ZoneType.RESIDENTIAL) {
                bridged++;
                assertNotNull(building, "residential placement must have a linked Building");
                assertInstanceOf(ResidentialBuilding.class, building,
                        "residential placement must be bridged to a ResidentialBuilding");
                ResidentialBuilding residential = (ResidentialBuilding) building;
                assertTrue(residential.getHouseholdCapacity() > 0, "household capacity must be positive");
                assertTrue(residential.getBedroomsPerHousehold() >= 1, "bedrooms per household must be at least 1");
                assertNotNull(residential.getPrice(), "residential price must be populated by the market");
                assertNotNull(residential.getRent(), "residential rent must be populated by the market");
                assertNotNull(residential.getDesirability(), "residential desirability must be populated");
                assertEquals(placed.getTemplate().getFloors(), residential.getFloors(),
                        "floors must be copied from the blueprint");
                Class<?> expectedSubtype =
                        placed.getTemplate().getFloors() >= ResidentialBuilding.HIGH_RISE_MIN_FLOORS
                                ? HighRiseResidential.class : LowRiseResidential.class;
                assertInstanceOf(expectedSubtype, residential,
                        "subtype must follow the blueprint's floors (template "
                                + placed.getTemplate().getCode() + ")");
            } else if (zone == ZoneType.COMMERCIAL || zone == ZoneType.INDUSTRIAL || zone == ZoneType.FARMLAND) {
                bridged++;
                assertNotNull(building, "business placement must have a linked Building");
                Class<?> expected = switch (zone) {
                    case COMMERCIAL -> CommercialBuilding.class;
                    case INDUSTRIAL -> IndustrialBuilding.class;
                    default -> AgriculturalBuilding.class;
                };
                assertInstanceOf(expected, building, "business placement must bridge to its sector type");
                assertNotNull(building.getPrice(), "business building must be priced by the business market");
            } else {
                assertNull(building, "public-use placement must not be bridged");
            }
        }
        assertEquals(bridged, reloaded.getEconomicBuildings().size(),
                "economicBuildings must contain every bridged residential + business building");

        // Households and their housing cross-links must round-trip: a populated
        // city carries residents, at least one placed into a home, and at least
        // one building with an owner (owner-occupier or landlord).
        assertFalse(reloaded.getHouseholds().isEmpty(), "the reloaded city must carry households");
        assertTrue(reloaded.getHouseholds().stream().anyMatch(h -> h.getResidence() != null),
                "at least one household must be housed");
        assertTrue(reloaded.getEconomicBuildings().stream().anyMatch(b -> b.getOwner() != null),
                "at least one building must have an owner");

        // The business market must have generated companies and placed at least
        // one into premises (a company that bought or rented a building).
        assertFalse(reloaded.getCompanies().isEmpty(), "the reloaded city must carry companies");
        assertTrue(reloaded.getCompanies().stream().anyMatch(c -> c.getPremises() != null),
                "at least one company must operate premises");

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
