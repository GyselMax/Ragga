package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.ZoneType;

/**
 * In-memory description of one building template - the shape the whole
 * pipeline works against. Phase B's DB-seeded BuildingTemplate entity maps
 * onto this same record, so generation code never depends on JPA.
 *
 * Footprints are always solid axis-aligned rectangles (any width x depth,
 * e.g. 2x4) - never holes or irregular shapes. Templates are authored with
 * their front facing NORTH; placement rotates them to the lot's actual road
 * frontage.
 *
 * @param code      stable human-readable key, e.g. "RES_HOUSE_2X3"
 * @param zone      which zone this building belongs in; null for public-use templates
 * @param publicUse true for public buildings (station, school, ...) which sit on
 *                  PUBLIC tiles instead of zoned lots
 * @param width     canonical (unrotated) footprint width in tiles
 * @param depth     canonical (unrotated) footprint depth in tiles
 * @param floors    vertical extent in floors, authored per blueprint. Placement
 *                  ignores it (footprint-only fitting); it drives the low-rise vs
 *                  high-rise split, the bedrooms-per-household derivation (floor
 *                  area = width x depth x floors), and the box height of future
 *                  3D/browser renders (height = floors x a floor-height constant)
 * @param householdCapacity number of households this building holds once occupied;
 *                  meaningful only for RESIDENTIAL templates, 0 for every other zone
 *                  and for public-use templates
 */
public record TemplateSpec(String code, ZoneType zone, boolean publicUse, int width, int depth,
                           int floors, int householdCapacity) {

    public int area() {
        return width * depth;
    }
}
