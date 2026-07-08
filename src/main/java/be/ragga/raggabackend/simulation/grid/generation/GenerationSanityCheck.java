package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.ZoneType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        violations(result).forEach(GenerationSanityCheck::report);
        printSummary(result);
    }

    /**
     * Check name -> violation count (0 = PASS), in display order. Public so
     * regression tests can assert on the invariants directly instead of
     * parsing console output.
     */
    public static Map<String, Long> violations(GenerationResult result) {
        Map<String, Long> checks = new LinkedHashMap<>();
        checks.put("every lot has road frontage", checkEveryLotHasFrontage(result));
        checks.put("every non-vacant lot has a building", checkOccupiedLotsHaveBuildings(result));
        checks.put("every footprint stays inside its own lot", checkFootprintsInsideLots(result));
        checks.put("no two building footprints overlap", checkNoFootprintOverlap(result));
        checks.put("no footprint cell sits on a road/park tile", checkNoBuildingOnImmutableTiles(result));
        checks.put("road network is fully connected", checkRoadConnectivity(result));
        return checks;
    }

    private static long checkEveryLotHasFrontage(GenerationResult result) {
        return result.lots().stream()
                .filter(lot -> lot.getFrontages().isEmpty())
                .count();
    }

    private static long checkOccupiedLotsHaveBuildings(GenerationResult result) {
        return result.lots().stream()
                .filter(lot -> !lot.isVacant() && lot.getBuilding() == null)
                .count();
    }

    private static long checkFootprintsInsideLots(GenerationResult result) {
        return result.lots().stream()
                .filter(lot -> lot.getBuilding() != null)
                .filter(lot -> {
                    BuildingDraft b = lot.getBuilding();
                    return b.x() < lot.getX() || b.y() < lot.getY()
                            || b.x() + b.sizeX() > lot.getX() + lot.getWidth()
                            || b.y() + b.sizeY() > lot.getY() + lot.getDepth();
                })
                .count();
    }

    private static long checkNoFootprintOverlap(GenerationResult result) {
        // Sweep over x-sorted buildings: once the next building starts past
        // this one's right edge, nothing later can overlap it either. Keeps
        // the check fast on large maps (tens of thousands of buildings).
        List<BuildingDraft> sorted = new java.util.ArrayList<>(result.buildings());
        sorted.sort(java.util.Comparator.comparingInt(BuildingDraft::x));

        long violations = 0;
        for (int i = 0; i < sorted.size(); i++) {
            BuildingDraft a = sorted.get(i);
            for (int j = i + 1; j < sorted.size(); j++) {
                BuildingDraft b = sorted.get(j);
                if (b.x() >= a.x() + a.sizeX()) {
                    break;
                }
                if (a.y() < b.y() + b.sizeY() && b.y() < a.y() + a.sizeY()) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private static long checkNoBuildingOnImmutableTiles(GenerationResult result) {
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
        return violations;
    }

    private static long checkRoadConnectivity(GenerationResult result) {
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
            return 1;
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
        return total - reachedCount;
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
