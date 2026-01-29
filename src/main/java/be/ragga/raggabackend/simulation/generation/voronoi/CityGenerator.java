package be.ragga.raggabackend.simulation.generation.voronoi;

import java.util.*;

/**
 * Main city generator orchestrator.
 * Coordinates voronoi generation, classification, and produces the final city.
 */
public class CityGenerator {
    private final double width;
    private final double height;
    private final int districtCount;
    private final long seed;
    private final Random random;

    private VoronoiDiagram voronoi;
    private DistrictClassifier classifier;
    private List<District> districts;

    /**
     * Create a city generator
     * @param width City width in pixels/units
     * @param height City height in pixels/units
     * @param districtCount Target number of districts (voronoi cells)
     * @param seed Random seed for reproducible generation
     */
    public CityGenerator(double width, double height, int districtCount, long seed) {
        this.width = width;
        this.height = height;
        this.districtCount = districtCount;
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * Generate the complete city
     */
    public void generate() {
        System.out.println("=== Starting City Generation ===");
        System.out.println("Dimensions: " + width + " x " + height);
        System.out.println("Target districts: " + districtCount);
        System.out.println("Seed: " + seed);
        System.out.println();

        // Step 1: Create voronoi diagram
        System.out.println("Step 1: Generating voronoi sites...");
        voronoi = new VoronoiDiagram(width, height, random);
        voronoi.generateSites(districtCount);
        System.out.println("  Generated " + voronoi.getDistricts().size() + " sites");

        // Step 2: Calculate voronoi cells
        System.out.println("Step 2: Calculating voronoi cells...");
        voronoi.calculateVoronoiCells();
        System.out.println("  Calculated cell boundaries");

        // Step 3: Get districts
        districts = voronoi.getDistricts();

        // Step 4: Classify districts
        System.out.println("Step 3: Classifying districts...");
        classifier = new DistrictClassifier(width, height, random);
        classifier.classifyDistricts(districts);
        System.out.println("  Classified " + districts.size() + " districts");

        // Step 5: Print statistics
        printStatistics();

        System.out.println();
        System.out.println("=== City Generation Complete ===");
    }

    /**
     * Print district distribution statistics
     */
    private void printStatistics() {
        System.out.println();
        System.out.println("District Distribution:");
        System.out.println("---------------------");

        Map<DistrictType, Integer> stats = classifier.getDistributionStats(districts);
        int total = districts.size();

        // Sort by count descending
        stats.entrySet().stream()
                .sorted(Map.Entry.<DistrictType, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    int count = entry.getValue();
                    double percentage = (count * 100.0) / total;
                    System.out.printf("  %-15s: %3d (%.1f%%)%n", 
                            entry.getKey().getDisplayName(), count, percentage);
                });

        // Desirability stats
        System.out.println();
        System.out.println("Desirability Stats:");
        System.out.println("-------------------");
        DoubleSummaryStatistics desirabilityStats = districts.stream()
                .mapToDouble(District::getDesirability)
                .summaryStatistics();
        System.out.printf("  Average: %.2f%n", desirabilityStats.getAverage());
        System.out.printf("  Min: %.2f%n", desirabilityStats.getMin());
        System.out.printf("  Max: %.2f%n", desirabilityStats.getMax());
    }

    /**
     * Get a district by ID
     */
    public District getDistrict(int id) {
        return districts.stream()
                .filter(d -> d.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all districts of a specific type
     */
    public List<District> getDistrictsByType(DistrictType type) {
        List<District> result = new ArrayList<>();
        for (District d : districts) {
            if (d.getType() == type) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * Get districts sorted by desirability
     */
    public List<District> getDistrictsByDesirability() {
        List<District> sorted = new ArrayList<>(districts);
        sorted.sort(Comparator.comparingDouble(District::getDesirability).reversed());
        return sorted;
    }

    /**
     * Find districts within a certain distance of a point
     */
    public List<District> getDistrictsNear(Point2D point, double radius) {
        List<District> nearby = new ArrayList<>();
        for (District d : districts) {
            if (d.getCenter().distanceTo(point) <= radius) {
                nearby.add(d);
            }
        }
        return nearby;
    }

    // Getters
    public List<District> getDistricts() {
        return new ArrayList<>(districts);
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public long getSeed() { return seed; }
}
