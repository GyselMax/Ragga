package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.GridPosition;
import be.ragga.raggabackend.simulation.grid.TileType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Shared spatial helpers for the generation stages: connected-component
 * flood fill over untyped cells and the largest axis-aligned rectangle
 * inscribed in an arbitrary cell region.
 */
final class RegionUtils {

    private RegionUtils() {
    }

    /** All 4-connected components of cells whose tile is still null (no road, no lot, nothing). */
    static List<List<GridPosition>> untypedRegions(TileType[][] tiles) {
        int width = tiles.length;
        int height = tiles[0].length;
        boolean[][] visited = new boolean[width][height];
        List<List<GridPosition>> regions = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] != null || visited[x][y]) {
                    continue;
                }
                List<GridPosition> region = new ArrayList<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[x][y] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    region.add(new GridPosition(cell[0], cell[1]));
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = cell[0] + d[0];
                        int ny = cell[1] + d[1];
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height
                                && tiles[nx][ny] == null && !visited[nx][ny]) {
                            visited[nx][ny] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                regions.add(region);
            }
        }
        return regions;
    }

    /**
     * Classic maximal-rectangle-in-binary-matrix: per row, keep a histogram
     * of consecutive filled cells above, then find the largest rectangle in
     * each histogram with a monotonic stack. O(region area).
     * Returns {x0, y0, x1, y1} in absolute grid coordinates.
     */
    static int[] largestInscribedRectangle(List<GridPosition> region) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GridPosition cell : region) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        boolean[][] filled = new boolean[w][h];
        for (GridPosition cell : region) {
            filled[cell.getX() - minX][cell.getY() - minY] = true;
        }

        int[] heights = new int[w];
        int bestArea = 0;
        int[] best = null;

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                heights[col] = filled[col][row] ? heights[col] + 1 : 0;
            }
            Deque<Integer> stack = new ArrayDeque<>();
            for (int col = 0; col <= w; col++) {
                int current = col == w ? 0 : heights[col];
                while (!stack.isEmpty() && heights[stack.peek()] >= current) {
                    int height = heights[stack.pop()];
                    int left = stack.isEmpty() ? 0 : stack.peek() + 1;
                    int area = height * (col - left);
                    if (area > bestArea) {
                        bestArea = area;
                        best = new int[]{minX + left, minY + row - height + 1, minX + col - 1, minY + row};
                    }
                }
                stack.push(col);
            }
        }
        return best;
    }
}
