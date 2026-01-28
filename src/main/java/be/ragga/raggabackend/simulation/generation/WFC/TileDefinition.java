// TileDefinitionImproved.java
package be.ragga.raggabackend.simulation.generation.WFC;

import java.util.*;

public class TileDefinition {

    /**
     * Helper class to build tiles with automatic bidirectional constraints
     */
    public static class SymmetricTileSetBuilder {
        private Map<String, Map<Direction, Set<String>>> adjacencyMap = new HashMap<>();
        private Map<String, Double> weights = new HashMap<>();

        public SymmetricTileSetBuilder addTile(String id, double weight) {
            adjacencyMap.putIfAbsent(id, new EnumMap<>(Direction.class));
            weights.put(id, weight);
            return this;
        }

        //  DEFINE A SYMMETRIC RELATIONSHIP: IF A CAN HAVE B TO IT'S DIRECTION,
        //  THEN B CAN HAVE A TO THE OPPOSITE DIRECTION
        public SymmetricTileSetBuilder connect(String tileA, Direction direction, String tileB) {
            // A -> direction -> B
            adjacencyMap.computeIfAbsent(tileA, k -> new EnumMap<>(Direction.class))
                    .computeIfAbsent(direction, k -> new HashSet<>())
                    .add(tileB);

            // B -> opposite direction -> A
            adjacencyMap.computeIfAbsent(tileB, k -> new EnumMap<>(Direction.class))
                    .computeIfAbsent(direction.opposite(), k -> new HashSet<>())
                    .add(tileA);

            return this;
        }

        //  CONNECT A TILE TO MULTIPLE NEIGHBORS IN A DIRECTION
        public SymmetricTileSetBuilder connect(String tile, Direction direction, String... neighbors) {
            for (String neighbor : neighbors) {
                connect(tile, direction, neighbor);
            }
            return this;
        }

        //  MAKE A TILE ABLE TO CONNECT TO ITSELF IN ALL DIRECTIONS
        public SymmetricTileSetBuilder selfConnecting(String tile) {
            for (Direction dir : Direction.values()) {
                connect(tile, dir, tile);
            }
            return this;
        }

        //  CONNECT TWO TILES IN ALL DIRECTIONS
        public SymmetricTileSetBuilder fullyConnect(String tileA, String tileB) {
            for (Direction dir : Direction.values()) {
                connect(tileA, dir, tileB);
            }
            return this;
        }

        public Map<String, Tile> build() {
            Map<String, Tile> tiles = new HashMap<>();

            for (String tileId : adjacencyMap.keySet()) {
                Tile.Builder builder = new Tile.Builder(tileId)
                        .weight(weights.getOrDefault(tileId, 1.0));

                Map<Direction, Set<String>> rules = adjacencyMap.get(tileId);
                for (Direction dir : Direction.values()) {
                    Set<String> neighbors = rules.getOrDefault(dir, Collections.emptySet());
                    if (!neighbors.isEmpty()) {
                        builder.validNeighbor(dir, neighbors);
                    }
                }

                tiles.put(tileId, builder.build());
            }

            return tiles;
        }
    }

    public static Map<String, Tile> createSimpleUrbanTileSet() {
        SymmetricTileSetBuilder builder = new SymmetricTileSetBuilder();

        // Define tiles with weights
        builder.addTile("empty", 0.4)
                .addTile("building", 5.0)
                .addTile("road_ns", 0.2)
                .addTile("road_ew", 0.2)
                .addTile("road_cross", 0.02);

        // Define connections - automatically bidirectional!

        // Empty can connect to everything
        builder.fullyConnect("empty", "empty")
                .fullyConnect("empty", "building");

        // Roads - vertical
        builder.connect("road_ns", Direction.NORTH, "road_ns", "road_cross")
                .connect("road_ns", Direction.SOUTH, "road_ns", "road_cross")
                .connect("road_ns", Direction.EAST, "empty", "building", "road_cross")
                .connect("road_ns", Direction.WEST, "empty", "building", "road_cross");

        // Roads - horizontal
        builder.connect("road_ew", Direction.NORTH, "empty", "building", "road_cross")
                .connect("road_ew", Direction.SOUTH, "empty", "building", "road_cross")
                .connect("road_ew", Direction.EAST, "road_ew", "road_cross")
                .connect("road_ew", Direction.WEST, "road_ew", "road_cross");

        // Crossroads
        builder.connect("road_cross", Direction.NORTH, "road_ns", "road_cross", "empty")
                .connect("road_cross", Direction.SOUTH, "road_ns", "road_cross", "empty")
                .connect("road_cross", Direction.EAST, "road_ew", "road_cross", "empty")
                .connect("road_cross", Direction.WEST, "road_ew", "road_cross", "empty");

        return builder.build();
    }
}