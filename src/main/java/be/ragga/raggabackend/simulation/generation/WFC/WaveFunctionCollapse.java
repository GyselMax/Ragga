// WaveFunctionCollapse.java
package be.ragga.raggabackend.simulation.generation.WFC;

import java.util.*;

public class WaveFunctionCollapse {
    private final Cell[][] grid;
    private final int width, height;
    private final Map<String, Tile> tileSet;
    private final Random random;
    private final long seed; // Store the seed

    public WaveFunctionCollapse(int width, int height, Map<String, Tile> tileSet) {
        this(width, height, tileSet, System.currentTimeMillis());
    }

    public WaveFunctionCollapse(int width, int height, Map<String, Tile> tileSet, long seed) {
        this.width = width;
        this.height = height;
        this.tileSet = tileSet;
        this.seed = seed;
        this.random = new Random(seed);
        this.grid = new Cell[width][height];

        initializeGrid();
    }

    private void initializeGrid() {
        Set<Tile> allTiles = new HashSet<>(tileSet.values());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Cell(allTiles);
            }
        }
    }

    public boolean generate() {
        while (!isFullyCollapsed()) {
            Point cellPos = findLowestEntropyCell();

            if (cellPos == null) {
                return false;
            }

            Cell cell = grid[cellPos.x][cellPos.y];
            Tile chosenTile = selectTile(cell);
            cell.collapse(chosenTile);

            if (!propagate(cellPos)) {
                return false;
            }
        }
        return true;
    }

    private Point findLowestEntropyCell() {
        Point minCell = null;
        double minEntropy = Double.MAX_VALUE;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Cell cell = grid[x][y];
                if (!cell.isCollapsed() && cell.getEntropy() > 0) {
                    double entropyWithNoise = cell.getEntropy() + random.nextDouble() * 0.1;
                    if (entropyWithNoise < minEntropy) {
                        minEntropy = entropyWithNoise;
                        minCell = new Point(x, y);
                    }
                }
            }
        }
        return minCell;
    }

    private Tile selectTile(Cell cell) {
        List<Tile> possibleTiles = new ArrayList<>(cell.getPossibleTiles());

        if (possibleTiles.isEmpty()) {
            throw new IllegalStateException("Cannot select from empty tile set");
        }

        double totalWeight = possibleTiles.stream()
                .mapToDouble(Tile::getWeight)
                .sum();

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Tile tile : possibleTiles) {
            cumulative += tile.getWeight();
            if (rand <= cumulative) {
                return tile;
            }
        }

        return possibleTiles.get(possibleTiles.size() - 1);
    }

    private boolean propagate(Point startPos) {
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty()) {
            Point pos = queue.poll();
            Cell cell = grid[pos.x][pos.y];

            for (Direction dir : Direction.values()) {
                int nx = pos.x + dir.dx;
                int ny = pos.y + dir.dy;

                if (!isValid(nx, ny)) continue;

                Cell neighbor = grid[nx][ny];
                if (neighbor.isCollapsed()) continue;

                Set<Tile> validNeighborTiles = getValidNeighborTiles(cell, dir);
                boolean changed = neighbor.constrain(validNeighborTiles);

                if (neighbor.getEntropy() == 0) {
                    return false;
                }

                if (changed) {
                    Point neighborPos = new Point(nx, ny);
                    if (!visited.contains(neighborPos)) {
                        queue.add(neighborPos);
                        visited.add(neighborPos);
                    }
                }
            }
        }
        return true;
    }

    private Set<Tile> getValidNeighborTiles(Cell cell, Direction direction) {
        Set<Tile> validTiles = new HashSet<>();

        for (Tile possibleTile : cell.getPossibleTiles()) {
            Set<String> validNeighborIds = possibleTile.getValidNeighbors(direction);

            for (String neighborId : validNeighborIds) {
                Tile neighborTile = tileSet.get(neighborId);
                if (neighborTile != null) {
                    validTiles.add(neighborTile);
                }
            }
        }

        return validTiles;
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private boolean isFullyCollapsed() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!grid[x][y].isCollapsed()) {
                    return false;
                }
            }
        }
        return true;
    }

    public Tile getTileAt(int x, int y) {
        if (!isValid(x, y)) {
            throw new IndexOutOfBoundsException("Position out of bounds");
        }
        return grid[x][y].getCollapsedTile();
    }

    public int getWidth() {return width;}
    public int getHeight() {return height;}
    public long getSeed() {return seed;}

    public void printGrid() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile tile = grid[x][y].getCollapsedTile();
                if (tile != null) {
                    System.out.print(tile.getId().charAt(0) + " ");
                } else {
                    System.out.print("? ");
                }
            }
            System.out.println();
        }
    }
}