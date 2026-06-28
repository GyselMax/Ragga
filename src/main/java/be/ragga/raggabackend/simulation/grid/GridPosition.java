package be.ragga.raggabackend.simulation.grid;

import jakarta.persistence.Embeddable;

@Embeddable
public class GridPosition {

    private int x;
    private int y;

    protected GridPosition() {
    }

    public GridPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(GridPosition other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
