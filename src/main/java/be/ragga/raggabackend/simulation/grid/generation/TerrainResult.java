package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;

/**
 * Output of Stage 0: the pre-seeded tile grid (WATER + FOREST + PARK
 * riverbanks, everything else null) plus the river geometry for stages that
 * need it (the density field's river lobe, bridge orientation).
 *
 * The tiles array is THE map array - the road generator draws into it, so
 * every pre-seeded non-null tile is automatically invisible to block
 * flood-fill, road splitting and lot subdivision.
 *
 * @param tiles           map-sized grid, null except terrain tiles
 * @param riverVertical   true = the river flows along y (centerline indexed
 *                        by y, holding x positions); false = along x
 * @param riverCenterline cross position of the river center per step along
 *                        the flow axis; null when no river was carved
 */
public record TerrainResult(TileType[][] tiles, boolean riverVertical, int[] riverCenterline) {

    public static TerrainResult noRiver(TileType[][] tiles) {
        return new TerrainResult(tiles, false, null);
    }

    public boolean hasRiver() {
        return riverCenterline != null;
    }

    /** True when a road of this orientation crosses the river as a bridge (perpendicular only). */
    public boolean mayBridge(boolean roadVertical) {
        return hasRiver() && roadVertical != riverVertical;
    }
}
