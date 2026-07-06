package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.road.RoadClass;

/**
 * In-memory road segment: a straight, axis-aligned, 1-tile-wide run from
 * (x0,y0) to (x1,y1) inclusive. Phase B persists these as RoadSegment
 * entities; ROAD tiles are always derived from segments, never the other
 * way around.
 */
public record RoadDraft(int x0, int y0, int x1, int y1, RoadClass roadClass) {
}
