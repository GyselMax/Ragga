package be.ragga.raggabackend.simulation.grid.generation;

import be.ragga.raggabackend.simulation.grid.TileType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Between terrain and roads: scatters hamlets and plans the countryside road
 * network. Everything here is destination-driven - there are no random rural
 * highways any more; a countryside road exists only because it connects two
 * settlements or reaches a map-edge exit.
 *
 * Hamlets are tiny density bumps: the road/block/lot/zone pipeline then
 * builds each village on its own, exactly as it builds the main city, because
 * every stage keys off the density field.
 *
 * Connection roads form one connected graph: each settlement links to its
 * nearest neighbors, a union pass guarantees a single component (so the road
 * network stays 100% connected - sanity invariant 6), and edge exits hang off
 * their nearest settlement.
 *
 * Stateless: config and randomness are passed in per call.
 */
@Component
public class SettlementPlanner {

    public SettlementPlan plan(GenerationConfig config, DensityField density,
                               TerrainResult terrain, Random random) {
        // Settlement centers that participate in the road graph: the existing
        // city cores (main + satellites, already in the field) come first,
        // then any hamlets we scatter.
        List<double[]> centers = new ArrayList<>();
        for (DensityField.Core core : density.cores()) {
            centers.add(new double[]{core.x(), core.y()});
        }

        List<DensityField.Core> hamletCores = scatterHamlets(config, density, terrain, random, centers);

        List<int[]> roads = new ArrayList<>();
        if (centers.size() > 1) {
            connectSettlements(centers, config, roads);
        }
        addEdgeExits(centers, config, random, roads);

        return new SettlementPlan(hamletCores, roads);
    }

    /** Rejection-samples village centers on genuinely rural, buildable land, spaced apart. */
    private List<DensityField.Core> scatterHamlets(GenerationConfig config, DensityField density,
                                                   TerrainResult terrain, Random random,
                                                   List<double[]> centers) {
        List<DensityField.Core> hamletCores = new ArrayList<>();
        if (config.hamletCount() == 0) {
            return hamletCores;
        }
        int radius = config.hamletRadius();
        double peak = (config.hamletPeakDensity() - config.edgeDensity()) / (1.0 - config.edgeDensity());
        int spanX = config.width() - 2 * radius;
        int spanY = config.height() - 2 * radius;
        if (spanX < 1 || spanY < 1) {
            return hamletCores; // map too small for a hamlet plus its clearance
        }

        int attempts = config.hamletCount() * 12;
        for (int a = 0; a < attempts && hamletCores.size() < config.hamletCount(); a++) {
            int x = radius + random.nextInt(spanX);
            int y = radius + random.nextInt(spanY);

            // Genuinely rural: outside every city's urban extent (the field
            // already includes satellites here).
            if (density.at(x, y) >= config.farmlandDensityThreshold()) {
                continue;
            }
            // Not on a natural feature - a village needs dry, open ground.
            TileType tile = terrain.tiles()[x][y];
            if (tile == TileType.WATER || tile == TileType.FOREST || tile == TileType.PARK) {
                continue;
            }
            // Spaced from every other settlement so villages don't clump.
            if (!spacedFromAll(centers, x, y, config.minSettlementSpacing())) {
                continue;
            }

            hamletCores.add(new DensityField.Core(x, y, radius, peak));
            centers.add(new double[]{x, y});
        }
        return hamletCores;
    }

    private boolean spacedFromAll(List<double[]> centers, int x, int y, int spacing) {
        for (double[] c : centers) {
            if (Math.hypot(x - c[0], y - c[1]) < spacing) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds the connection graph: every settlement links to its N nearest
     * neighbors, then a union-find pass adds the shortest cross-component
     * edges until the whole set is one connected network.
     */
    private void connectSettlements(List<double[]> centers, GenerationConfig config, List<int[]> roads) {
        int n = centers.size();
        boolean[][] linked = new boolean[n][n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        // N nearest neighbors per settlement.
        for (int i = 0; i < n; i++) {
            for (int neighbor : nearestNeighbors(centers, i, config.settlementConnectionCount())) {
                addLink(centers, roads, linked, parent, i, neighbor);
            }
        }

        // Union pass: while more than one component remains, add the shortest
        // edge joining two different components. Guarantees connectivity.
        while (componentCount(parent) > 1) {
            int bestA = -1;
            int bestB = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (find(parent, i) == find(parent, j)) {
                        continue;
                    }
                    double d = distance(centers, i, j);
                    if (d < bestDist) {
                        bestDist = d;
                        bestA = i;
                        bestB = j;
                    }
                }
            }
            addLink(centers, roads, linked, parent, bestA, bestB);
        }
    }

    private void addLink(List<double[]> centers, List<int[]> roads, boolean[][] linked, int[] parent,
                         int a, int b) {
        if (a == b || linked[a][b]) {
            return;
        }
        linked[a][b] = true;
        linked[b][a] = true;
        union(parent, a, b);
        roads.add(new int[]{
                (int) Math.round(centers.get(a)[0]), (int) Math.round(centers.get(a)[1]),
                (int) Math.round(centers.get(b)[0]), (int) Math.round(centers.get(b)[1])});
    }

    private List<Integer> nearestNeighbors(List<double[]> centers, int from, int count) {
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < centers.size(); i++) {
            if (i != from) {
                others.add(i);
            }
        }
        others.sort((p, q) -> Double.compare(distance(centers, from, p), distance(centers, from, q)));
        return others.subList(0, Math.min(count, others.size()));
    }

    /** Adds edge exits: roads that run from a border point to their nearest settlement. */
    private void addEdgeExits(List<double[]> centers, GenerationConfig config, Random random, List<int[]> roads) {
        if (centers.isEmpty()) {
            return;
        }
        for (int i = 0; i < config.edgeExitCount(); i++) {
            int[] border = randomBorderPoint(config, random);
            int nearest = 0;
            double bestDist = Double.MAX_VALUE;
            for (int c = 0; c < centers.size(); c++) {
                double d = Math.hypot(border[0] - centers.get(c)[0], border[1] - centers.get(c)[1]);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = c;
                }
            }
            roads.add(new int[]{border[0], border[1],
                    (int) Math.round(centers.get(nearest)[0]), (int) Math.round(centers.get(nearest)[1])});
        }
    }

    private int[] randomBorderPoint(GenerationConfig config, Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new int[]{random.nextInt(config.width()), 0};
            case 1 -> new int[]{random.nextInt(config.width()), config.height() - 1};
            case 2 -> new int[]{0, random.nextInt(config.height())};
            default -> new int[]{config.width() - 1, random.nextInt(config.height())};
        };
    }

    private double distance(List<double[]> centers, int a, int b) {
        return Math.hypot(centers.get(a)[0] - centers.get(b)[0], centers.get(a)[1] - centers.get(b)[1]);
    }

    // --- tiny union-find ---

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }

    private int componentCount(int[] parent) {
        int roots = 0;
        for (int i = 0; i < parent.length; i++) {
            if (find(parent, i) == i) {
                roots++;
            }
        }
        return roots;
    }
}
