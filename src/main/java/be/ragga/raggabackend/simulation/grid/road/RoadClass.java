package be.ragga.raggabackend.simulation.grid.road;

/**
 * Road hierarchy, ordered from most to least major. Ordinal order matters:
 * lower ordinal = more major road (used when two roads cross on the same
 * tile - the more major class wins).
 */
public enum RoadClass {
    ARTERIAL,
    COLLECTOR,
    LOCAL
}
