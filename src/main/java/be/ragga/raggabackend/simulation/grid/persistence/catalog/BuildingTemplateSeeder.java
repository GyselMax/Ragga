package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.generation.StubTemplateCatalog;
import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds the building-template table from {@link StubTemplateCatalog} the first
 * time the app boots against an empty catalog. Idempotent: on any later boot
 * the table is already populated, so it does not re-insert - which is why the
 * live catalog can grow beyond this starter set without the seeder ever
 * clobbering it. StubTemplateCatalog stays the single source of truth for the
 * starter library and keeps working standalone for GridVisualizer.
 *
 * It DOES backfill columns that ddl-auto=update added after a row was first
 * seeded: `update` alters the table with a zero default on existing rows (it
 * cannot know the authored values), so a stub-known row whose householdCapacity
 * or floors is still 0 gets the stub's value on the next boot. Hand-edited
 * nonzero values are never touched.
 */
@Component
public class BuildingTemplateSeeder implements CommandLineRunner {

    private final BuildingTemplateRepository repository;

    public BuildingTemplateSeeder(BuildingTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            StubTemplateCatalog.standard().stream()
                    .map(BuildingTemplate::from)
                    .forEach(repository::save);
            return;
        }

        Map<String, TemplateSpec> stubByCode = StubTemplateCatalog.standard().stream()
                .collect(Collectors.toMap(TemplateSpec::code, Function.identity()));
        for (BuildingTemplate template : repository.findAll()) {
            TemplateSpec stub = stubByCode.get(template.getCode());
            if (stub == null) {
                continue;
            }
            boolean changed = false;
            if (template.getHouseholdCapacity() == 0 && stub.householdCapacity() > 0) {
                template.setHouseholdCapacity(stub.householdCapacity());
                changed = true;
            }
            if (template.getFloors() == 0 && stub.floors() > 0) {
                template.setFloors(stub.floors());
                changed = true;
            }
            if (changed) {
                repository.save(template);
            }
        }
    }
}
