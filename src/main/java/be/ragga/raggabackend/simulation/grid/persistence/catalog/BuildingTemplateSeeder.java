package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.generation.StubTemplateCatalog;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the building-template table from {@link StubTemplateCatalog} the first
 * time the app boots against an empty catalog. Idempotent: on any later boot
 * the table is already populated, so it does nothing - which is why the live
 * catalog can grow beyond this starter set without the seeder ever clobbering
 * it. StubTemplateCatalog stays the single source of truth for the starter
 * library and keeps working standalone for GridVisualizer.
 */
@Component
public class BuildingTemplateSeeder implements CommandLineRunner {

    private final BuildingTemplateRepository repository;

    public BuildingTemplateSeeder(BuildingTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        StubTemplateCatalog.standard().stream()
                .map(BuildingTemplate::from)
                .forEach(repository::save);
    }
}
