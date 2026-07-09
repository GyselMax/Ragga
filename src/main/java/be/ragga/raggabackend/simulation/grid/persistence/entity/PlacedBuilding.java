package be.ragga.raggabackend.simulation.grid.persistence.entity;

import be.ragga.raggabackend.simulation.building.Building;
import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import jakarta.persistence.*;

/**
 * A persisted, physically placed building - the finished form of a
 * generation-time {@code BuildingDraft}. This is primarily the PHYSICAL
 * placement record (which template was stamped, where, which way it faces);
 * the optional {@code building} link attaches the economic simulation object
 * ({@code Building} hierarchy - price/rent/etc.) once one exists for this
 * placement. Null for public-use and vacant lots, and for zones not yet
 * bridged to the economic side.
 *
 * {@code origin} is the top-left corner of the effective footprint;
 * {@code sizeX}/{@code sizeY} are the effective (possibly rotated) footprint,
 * which differ from the template's canonical width/depth when the building was
 * rotated 90/270 degrees to face its road.
 */
@Entity
public class PlacedBuilding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Embedded
    private GridPosition origin;

    private int sizeX;
    private int sizeY;

    @Enumerated(EnumType.STRING)
    private Direction facing;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private BuildingTemplate template;

    // The lot this building sits on; null for public-use buildings, which sit
    // on PUBLIC tiles rather than zoned lots.
    @ManyToOne
    @JoinColumn(name = "lot_id")
    private Lot lot;

    // The economic Building instance for this placement, if one has been
    // bridged yet (see class doc). Nullable.
    @OneToOne
    @JoinColumn(name = "building_id")
    private Building building;

    protected PlacedBuilding() {
    }

    public PlacedBuilding(GridPosition origin, int sizeX, int sizeY, Direction facing,
                          BuildingTemplate template, Lot lot, Building building) {
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.facing = facing;
        this.template = template;
        this.lot = lot;
        this.building = building;
    }

    public long getId() {
        return id;
    }

    public GridPosition getOrigin() {
        return origin;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public Direction getFacing() {
        return facing;
    }

    public BuildingTemplate getTemplate() {
        return template;
    }

    public Lot getLot() {
        return lot;
    }

    public Building getBuilding() {
        return building;
    }
}
