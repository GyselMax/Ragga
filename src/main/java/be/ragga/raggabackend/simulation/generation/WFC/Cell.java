// Cell.java
package be.ragga.raggabackend.simulation.generation.WFC;

import java.util.*;

public class Cell {
    private Set<Tile> possibleTiles;
    private Tile collapsedTile;
    private int entropy;

    public Cell(Set<Tile> initialPossibleTiles) {
        this.possibleTiles = new HashSet<>(initialPossibleTiles);
        this.collapsedTile = null;
        this.entropy = possibleTiles.size();
    }

    public Cell(Cell other) {
        this.possibleTiles = new HashSet<>(other.possibleTiles);
        this.collapsedTile = other.collapsedTile;
        this.entropy = other.entropy;
    }

    public boolean isCollapsed() {
        return collapsedTile != null;
    }

    public void collapse(Tile tile) {
        this.collapsedTile = tile;
        this.possibleTiles = Set.of(tile);
        this.entropy = 1;
    }

    public boolean constrain(Set<Tile> allowedTiles) {
        int oldSize = possibleTiles.size();
        possibleTiles.retainAll(allowedTiles);
        entropy = possibleTiles.size();
        return possibleTiles.size() < oldSize;
    }

    public Set<Tile> getPossibleTiles() {return new HashSet<>(possibleTiles);}
    public Tile getCollapsedTile() {return collapsedTile;}
    public int getEntropy() {return entropy;}
}