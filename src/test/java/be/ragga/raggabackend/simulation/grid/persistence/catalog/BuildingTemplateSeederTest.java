package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.generation.StubTemplateCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the seeder's three behaviors: initial seeding of an empty catalog,
 * backfilling columns on rows that predate them (ddl-auto=update leaves new
 * columns at 0 on existing rows), and never clobbering a hand-edited nonzero
 * value. H2, same slice setup as CityPersistenceRoundTripTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BuildingTemplateSeederTest {

    @Autowired
    private BuildingTemplateRepository repository;

    @Test
    void seedsEmptyCatalogFromStub() {
        new BuildingTemplateSeeder(repository).run();

        assertEquals(StubTemplateCatalog.standard().size(), repository.count(),
                "empty catalog must be seeded with the full stub library");
    }

    @Test
    void backfillsCapacityAndFloorsOnPreColumnRows() {
        // Simulate a row seeded before householdCapacity/floors existed:
        // ddl-auto=update adds both columns with 0 on existing rows.
        repository.save(new BuildingTemplate("RES_HOUSE_2X2", ZoneType.RESIDENTIAL, false, 2, 2, 0, 0));

        new BuildingTemplateSeeder(repository).run();

        BuildingTemplate backfilled = repository.findAll().stream()
                .filter(t -> t.getCode().equals("RES_HOUSE_2X2"))
                .findFirst().orElseThrow();
        assertEquals(1, backfilled.getHouseholdCapacity(),
                "stub-known residential row stuck at 0 capacity must be backfilled from the stub");
        assertEquals(2, backfilled.getFloors(),
                "stub-known row stuck at 0 floors must be backfilled from the stub");
        // Non-empty table: the seeder must not have re-inserted the library.
        assertEquals(1, repository.count(), "backfill must not re-seed the catalog");
    }

    @Test
    void neverClobbersHandEditedValues() {
        // A live-game admin bumped this template's capacity and floors; the
        // seeder must leave both alone even though the stub disagrees.
        repository.save(new BuildingTemplate("RES_HOUSE_2X2", ZoneType.RESIDENTIAL, false, 2, 2, 4, 7));

        new BuildingTemplateSeeder(repository).run();

        BuildingTemplate untouched = repository.findAll().stream()
                .filter(t -> t.getCode().equals("RES_HOUSE_2X2"))
                .findFirst().orElseThrow();
        assertEquals(7, untouched.getHouseholdCapacity(),
                "hand-edited nonzero capacity must never be overwritten by the seeder");
        assertEquals(4, untouched.getFloors(),
                "hand-edited nonzero floors must never be overwritten by the seeder");
    }
}
