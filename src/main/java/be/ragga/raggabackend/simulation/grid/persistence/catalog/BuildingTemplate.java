package be.ragga.raggabackend.simulation.grid.persistence.catalog;

import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import jakarta.persistence.*;

/**
 * A building-catalog row - the DB-backed equivalent of the generator's
 * in-memory {@link TemplateSpec}. Kept as its own table (seeded from
 * StubTemplateCatalog at boot) so the library can grow over a live game
 * without redeploys. Generation never sees this entity: {@code DbTemplateCatalog}
 * maps it to {@link TemplateSpec} first.
 */
@Entity
public class BuildingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // Stable human-readable key, e.g. "RES_HOUSE_2X3". Unique so the seeder is
    // idempotent and placed buildings can be matched back to their template.
    @Column(unique = true, nullable = false)
    private String code;

    // Which zone this belongs in; null for public-use templates.
    @Enumerated(EnumType.STRING)
    private ZoneType zone;

    private boolean publicUse;

    // Canonical (unrotated) footprint in tiles.
    private int width;
    private int depth;

    protected BuildingTemplate() {
    }

    public BuildingTemplate(String code, ZoneType zone, boolean publicUse, int width, int depth) {
        this.code = code;
        this.zone = zone;
        this.publicUse = publicUse;
        this.width = width;
        this.depth = depth;
    }

    public static BuildingTemplate from(TemplateSpec spec) {
        return new BuildingTemplate(spec.code(), spec.zone(), spec.publicUse(), spec.width(), spec.depth());
    }

    public TemplateSpec toSpec() {
        return new TemplateSpec(code, zone, publicUse, width, depth);
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public ZoneType getZone() {
        return zone;
    }

    public boolean isPublicUse() {
        return publicUse;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }
}
