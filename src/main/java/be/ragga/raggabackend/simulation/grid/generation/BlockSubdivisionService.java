package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.TileType;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Stage 2: flood-fills the space between roads into contiguous blocks.
 * Blocks below the minimum area are too small to ever hold a lot and become
 * roadside PARK strips immediately (green pockets read better than dead
 * gray slivers, and bent arterials produce many of them).
 */
@Component
public class BlockSubdivisionService {

    public List<Block> findBlocks(TileType[][] tiles, GenerationConfig config) {
        int width = config.width();
        int height = config.height();
        boolean[][] visited = new boolean[width][height];
        List<Block> blocks = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] != null || visited[x][y]) {
                    continue;
                }
                Block block = floodFill(x, y, tiles, visited, width, height);
                if (block.area() < config.minBlockArea()) {
                    // Too small for lots - becomes roadside green rather than
                    // a dead gray sliver (bent roads produce many of these).
                    for (GridPosition cell : block.cells()) {
                        tiles[cell.getX()][cell.getY()] = TileType.PARK;
                    }
                } else {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    private Block floodFill(int startX, int startY, TileType[][] tiles, boolean[][] visited, int width, int height) {
        List<GridPosition> cells = new ArrayList<>();
        int minX = startX, minY = startY, maxX = startX, maxY = startY;

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int cx = cell[0];
            int cy = cell[1];
            cells.add(new GridPosition(cx, cy));
            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            maxX = Math.max(maxX, cx);
            maxY = Math.max(maxY, cy);

            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height
                        && tiles[nx][ny] == null && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return new Block(cells, minX, minY, maxX, maxY);
    }
}
