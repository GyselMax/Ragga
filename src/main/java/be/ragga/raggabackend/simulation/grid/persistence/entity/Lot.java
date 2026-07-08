package be.ragga.raggabackend.simulation.grid.persistence.entity;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.ZoneType;
import jakarta.persistence.*;

/**
 * A persisted, player-ownable parcel - the finished form of a generation-time
 * {@code LotDraft}. Always a solid axis-aligned rectangle: {@code origin} is
 * its top-left cell, spanning {@code width} tiles along x and {@code depth}
 * along y.
 *
 * Generation-only artifacts (frontages, the zone-lock flag) are intentionally
 * dropped - they exist to drive zoning during generation and carry no meaning
 * for the stored city or the simulation.
 */
@Entity
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Embedded
    private GridPosition origin;

    private int width;
    private int depth;

    @Enumerated(EnumType.STRING)
    private ZoneType zone;

    // Zoned but unbuilt: the cheap parcels a player can buy and develop later.
    private boolean vacant;

    // Sits on a public-use site (school, station, ...) rather than being a
    // normal zoned lot.
    private boolean publicSite;

    // The building stamped on this lot, if any. Owned by PlacedBuilding (it
    // holds the FK); null for vacant or public lots without a building.
    @OneToOne(mappedBy = "lot")
    private PlacedBuilding building;

    protected Lot() {
    }

    public Lot(GridPosition origin, int width, int depth, ZoneType zone, boolean vacant, boolean publicSite) {
        this.origin = origin;
        this.width = width;
        this.depth = depth;
        this.zone = zone;
        this.vacant = vacant;
        this.publicSite = publicSite;
    }

    public long getId() {
        return id;
    }

    public GridPosition getOrigin() {
        return origin;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public ZoneType getZone() {
        return zone;
    }

    public boolean isVacant() {
        return vacant;
    }

    public boolean isPublicSite() {
        return publicSite;
    }

    public PlacedBuilding getBuilding() {
        return building;
    }

    public void setBuilding(PlacedBuilding building) {
        this.building = building;
    }
}
