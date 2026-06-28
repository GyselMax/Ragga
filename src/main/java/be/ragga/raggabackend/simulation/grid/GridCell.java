package be.ragga.raggabackend.simulation.grid;

import jakarta.persistence.*;

@Entity
public class GridCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Embedded
    private GridPosition position;

    @Enumerated(EnumType.STRING)
    private ZoneType zoneType;

    protected GridCell() {
    }

    public GridCell(GridPosition position, ZoneType zoneType) {
        this.position = position;
        this.zoneType = zoneType;
    }

    public long getId() {
        return id;
    }

    public GridPosition getPosition() {
        return position;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public void setZoneType(ZoneType zoneType) {
        this.zoneType = zoneType;
    }
}
