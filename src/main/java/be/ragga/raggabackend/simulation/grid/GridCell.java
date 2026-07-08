package be.ragga.raggabackend.simulation.grid;

import jakarta.persistence.*;

/**
 * One persisted grid tile - the map's physical raster layer, one row per cell.
 *
 * A cell only knows what physically sits on it ({@link TileType}); zoning is a
 * property of a {@link be.ragga.raggabackend.simulation.grid.persistence.entity.Lot},
 * never of a cell (see the TileType/ZoneType docs). The full {@code tiles[][]}
 * grid a city was generated with is reconstructable by reading these back.
 */
@Entity
public class GridCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Embedded
    private GridPosition position;

    @Enumerated(EnumType.STRING)
    private TileType tileType;

    protected GridCell() {
    }

    public GridCell(GridPosition position, TileType tileType) {
        this.position = position;
        this.tileType = tileType;
    }

    public long getId() {
        return id;
    }

    public GridPosition getPosition() {
        return position;
    }

    public TileType getTileType() {
        return tileType;
    }

    public void setTileType(TileType tileType) {
        this.tileType = tileType;
    }
}
