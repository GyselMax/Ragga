package be.ragga.raggabackend.simulation.grid.persistence.entity;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import jakarta.persistence.*;

/**
 * A persisted, physically placed building - the finished form of a
 * generation-time {@code BuildingDraft}. This is the PHYSICAL placement record
 * only (which template was stamped, where, which way it faces); the economic
 * simulation object (the {@code Building} hierarchy with price/rent/etc.) is a
 * separate concern that a later phase can attach to this placement.
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

    protected PlacedBuilding() {
    }

    public PlacedBuilding(GridPosition origin, int sizeX, int sizeY, Direction facing,
                          BuildingTemplate template, Lot lot) {
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.facing = facing;
        this.template = template;
        this.lot = lot;
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
}
