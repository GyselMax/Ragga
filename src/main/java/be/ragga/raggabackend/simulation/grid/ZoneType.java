package be.ragga.raggabackend.simulation.grid;

/**
 * What occupies a Lot. Lots are the player-ownable parcels of the map, so
 * these values only cover kinds of development a player can own. Roads,
 * parks and public buildings are not zones - they are physical tile types
 * (see TileType) and can never be player property.
 */
public enum ZoneType {
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,

    // A player-owned lot whose building is currently being built. Live-game
    // state only: the procedural generator builds instantly and never emits
    // this.
    UNDER_CONSTRUCTION,

    // Agricultural parcels on the rural rim - huge, sparse lots assigned by
    // the generator below the farmland density threshold. Player-ownable
    // like any other lot (cheap land to develop later).
    FARMLAND
}
