package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.ZoneType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assertion-free invariant audit over a generation result: prints PASS/FAIL
 * per check plus a summary, instead of throwing. Runs after GridVisualizer's
 * generation so a broken invariant is spotted next to the rendered PNG.
 */
public final class GenerationSanityCheck {

    private GenerationSanityCheck() {
    }

    public static void run(GenerationResult result) {
        System.out.println("--- generation sanity check ---");
        checkEveryLotHasFrontage(result);
        checkOccupiedLotsHaveBuildings(result);
        checkFootprintsInsideLots(result);
        checkNoFootprintOverlap(result);
        checkNoBuildingOnImmutableTiles(result);
        checkRoadConnectivity(result);
        printSummary(result);
    }

    private static void checkEveryLotHasFrontage(GenerationResult result) {
        long violations = result.lots().stream()
                .filter(lot -> lot.getFrontages().isEmpty())
                .count();
        report("every lot has road frontage", violations);
    }

    private static void checkOccupiedLotsHaveBuildings(GenerationResult result) {
        long violations = result.lots().stream()
                .filter(lot -> !lot.isVacant() && lot.getBuilding() == null)
                .count();
        report("every non-vacant lot has a building", violations);
    }

    private static void checkFootprintsInsideLots(GenerationResult result) {
        long violations = result.lots().stream()
                .filter(lot -> lot.getBuilding() != null)
                .filter(lot -> {
                    BuildingDraft b = lot.getBuilding();
                    return b.x() < lot.getX() || b.y() < lot.getY()
                            || b.x() + b.sizeX() > lot.getX() + lot.getWidth()
                            || b.y() + b.sizeY() > lot.getY() + lot.getDepth();
                })
                .count();
        report("every footprint stays inside its own lot", violations);
    }

    private static void checkNoFootprintOverlap(GenerationResult result) {
        // Brute-force rectangle intersection - trivially fast at this scale.
        List<BuildingDraft> buildings = result.buildings();
        long violations = 0;
        for (int i = 0; i < buildings.size(); i++) {
            for (int j = i + 1; j < buildings.size(); j++) {
                BuildingDraft a = buildings.get(i);
                BuildingDraft b = buildings.get(j);
                boolean overlap = a.x() < b.x() + b.sizeX() && b.x() < a.x() + a.sizeX()
                        && a.y() < b.y() + b.sizeY() && b.y() < a.y() + a.sizeY();
                if (overlap) {
                    violations++;
                }
            }
        }
        report("no two building footprints overlap", violations);
    }

    private static void checkNoBuildingOnImmutableTiles(GenerationResult result) {
        // ROAD and PARK may never carry a building; PUBLIC only carries the
        // public building whose parcel it is.
        long violations = 0;
        for (BuildingDraft building : result.buildings()) {
            TileType expected = building.template().publicUse() ? TileType.PUBLIC : TileType.LOT;
            for (int x = building.x(); x < building.x() + building.sizeX(); x++) {
                for (int y = building.y(); y < building.y() + building.sizeY(); y++) {
                    if (result.tiles()[x][y] != expected) {
                        violations++;
                    }
                }
            }
        }
        report("no footprint cell sits on a road/park tile", violations);
    }

    private static void checkRoadConnectivity(GenerationResult result) {
        TileType[][] tiles = result.tiles();
        int width = result.config().width();
        int height = result.config().height();

        int total = 0;
        int startX = -1;
        int startY = -1;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (tiles[x][y] == TileType.ROAD) {
                    total++;
                    if (startX < 0) {
                        startX = x;
                        startY = y;
                    }
                }
            }
        }
        if (total == 0) {
            report("road network is fully connected", 1);
            return;
        }

        boolean[][] reached = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        reached[startX][startY] = true;
        int reachedCount = 0;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            reachedCount++;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cell[0] + d[0];
                int ny = cell[1] + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height
                        && tiles[nx][ny] == TileType.ROAD && !reached[nx][ny]) {
                    reached[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        report("road network is fully connected", total - reachedCount);
    }

    private static void printSummary(GenerationResult result) {
        Map<ZoneType, Integer> zoneCounts = new EnumMap<>(ZoneType.class);
        int vacant = 0;
        int publicSites = 0;
        for (LotDraft lot : result.lots()) {
            zoneCounts.merge(lot.getZone(), 1, Integer::sum);
            if (lot.isVacant()) vacant++;
            if (lot.isPublicSite()) publicSites++;
        }

        Map<TileType, Integer> tileCounts = new EnumMap<>(TileType.class);
        for (int x = 0; x < result.config().width(); x++) {
            for (int y = 0; y < result.config().height(); y++) {
                tileCounts.merge(result.tiles()[x][y], 1, Integer::sum);
            }
        }

        System.out.printf("roads: %d segments, %d tiles%n", result.roads().size(), tileCounts.getOrDefault(TileType.ROAD, 0));
        System.out.printf("lots: %d (%s), vacant: %d, public sites: %d%n",
                result.lots().size(), zoneCounts, vacant, publicSites);
        System.out.printf("buildings: %d | park tiles: %d | unused tiles: %d%n",
                result.buildings().size(),
                tileCounts.getOrDefault(TileType.PARK, 0),
                tileCounts.getOrDefault(TileType.UNUSED, 0));
    }

    private static void report(String check, long violations) {
        if (violations == 0) {
            System.out.println("PASS  " + check);
        } else {
            System.out.println("FAIL  " + check + " (" + violations + " violations)");
        }
    }
}
