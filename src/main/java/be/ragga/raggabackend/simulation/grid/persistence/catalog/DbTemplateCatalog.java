package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The live-game catalog: reads {@link BuildingTemplate} rows and hands the
 * generator plain {@link TemplateSpec}s. Because this is the seam between the
 * DB and the JPA-free generation package, the mapping to TemplateSpec happens
 * here and nowhere downstream.
 */
@Component
public class DbTemplateCatalog implements TemplateCatalog {

    private final BuildingTemplateRepository repository;

    public DbTemplateCatalog(BuildingTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TemplateSpec> templates() {
        return repository.findAll().stream()
                .map(BuildingTemplate::toSpec)
                .toList();
    }
}
