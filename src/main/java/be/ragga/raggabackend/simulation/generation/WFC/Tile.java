// Tile.java
package be.ragga.raggabackend.simulation.generation.WFC;

import java.util.*;

public class Tile {
    private final String id;
    private final double weight;
    private final Map<Direction, Set<String>> adjacencyRules;

    private Tile(Builder builder) {
        this.id = builder.id;
        this.weight = builder.weight;
        this.adjacencyRules = builder.adjacencyRules;
    }

    public String getId() {
        return id;
    }
    public double getWeight() {
        return weight;
    }
    public Set<String> getValidNeighbors(Direction direction) {
        return adjacencyRules.getOrDefault(direction, Collections.emptySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tile)) return false;
        Tile tile = (Tile) o;
        return id.equals(tile.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }

    public static class Builder {
        private final String id;
        private double weight = 1.0;
        private Map<Direction, Set<String>> adjacencyRules = new EnumMap<>(Direction.class);

        public Builder(String id) {
            this.id = id;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder validNeighbor(Direction direction, Set<String> validTileIds) {
            adjacencyRules.put(direction, new HashSet<>(validTileIds));
            return this;
        }

        public Tile build() {
            return new Tile(this);
        }
    }
}