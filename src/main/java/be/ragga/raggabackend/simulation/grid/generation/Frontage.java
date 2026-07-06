package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.road.RoadClass;

/**
 * One road-touching side of a lot: how many of that side's tiles actually
 * border road, and the most major road class among them (an arterial
 * frontage makes a lot more attractive for commercial zoning).
 */
public record Frontage(int lengthTiles, RoadClass roadClass) {
}
