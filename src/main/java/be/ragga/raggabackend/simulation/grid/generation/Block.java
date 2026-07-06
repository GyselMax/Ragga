package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;

import java.util.List;

/**
 * Transient connected area of non-road cells, bounded by roads and/or map
 * edges. Only exists to be sliced into lots - never persisted, nothing
 * downstream queries blocks.
 */
public record Block(List<GridPosition> cells, int minX, int minY, int maxX, int maxY) {

    public int area() {
        return cells.size();
    }

    public boolean isRectangular() {
        return cells.size() == (maxX - minX + 1) * (maxY - minY + 1);
    }
}
