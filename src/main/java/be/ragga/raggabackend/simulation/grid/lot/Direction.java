package be.ragga.raggabackend.simulation.grid.lot;

/**
 * Compass direction on the grid. The grid's y axis grows downward (row
 * index), so NORTH is negative y - matching how the visualizer renders with
 * y 0 at the top.
 */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }
}
