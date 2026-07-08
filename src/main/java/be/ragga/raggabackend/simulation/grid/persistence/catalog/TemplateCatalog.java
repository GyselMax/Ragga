package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;

import java.util.List;

/**
 * Supplies the building library the generation pipeline works against, as plain
 * {@link TemplateSpec} records. This indirection is what keeps generation
 * JPA-free: the pipeline asks a TemplateCatalog for specs and never touches the
 * {@link BuildingTemplate} entity or the database.
 */
public interface TemplateCatalog {

    List<TemplateSpec> templates();
}
