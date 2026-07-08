package be.ragga.raggabackend.simulation.grid;

/**
 * Physical type of a single grid cell - the map layer.
 * Zoning is a property of a Lot (see ZoneType), not of a cell: a cell only
 * knows what physically sits on it.
 */
public enum TileType {

    ROAD,

    // Part of a player-ownable Lot (the only buildable ground).
    LOT,

    // Park land. Parks are not Lots - interior parcels that end up without
    // road access simply become park tiles.
    PARK,

    // Occupied by a public-use building (station, school, library, ...).
    // Also not a Lot.
    PUBLIC,

    // Leftover sliver too small to be anything.
    UNUSED,

    // River/lake water carved by the terrain stage, before any road exists.
    // Never buildable, never a lot; only arterial bridges may cross it.
    WATER,

    // Woodland carved by the terrain stage where density is low - the
    // corners and the rural rim. Never buildable; arterials cut through.
    FOREST;

    /**
     * Only LOT cells can ever receive a building footprint. ROAD, PARK and
     * PUBLIC are immutable to building placement by design - changing them is
     * a deliberate (future) admin operation, never a side effect of placing
     * a building. This check is authoritative server-side, regardless of what
     * a future frontend allows.
     */
    public boolean isBuildable() {
        return this == LOT;
    }
}
