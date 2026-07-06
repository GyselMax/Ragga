package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;

import java.util.List;

/**
 * Output of road generation: the segment list (authoritative graph) plus the
 * tile grids derived from it. tiles[x][y] is ROAD where a road runs and null
 * everywhere else at this stage; later pipeline stages fill in the rest.
 * roadClasses[x][y] holds the most major class crossing that tile.
 */
public record RoadNetwork(List<RoadDraft> roads, TileType[][] tiles, RoadClass[][] roadClasses) {
}
