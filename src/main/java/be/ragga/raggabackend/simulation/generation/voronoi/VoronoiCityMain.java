package be.ragga.raggabackend.simulation.generation.voronoi;

/**
 * Main entry point for city generation.
 * Run this to generate and visualize a city.
 */
public class VoronoiCityMain {
    public static void main(String[] args) {
        // Configuration
        double cityWidth = 1200;   // Width in pixels/units
        double cityHeight = 800;   // Height in pixels/units
        int districtCount = 150;   // Number of districts (voronoi cells)
        long seed = 42L;           // Random seed for reproducibility

        // Create and generate city
        CityGenerator generator = new CityGenerator(cityWidth, cityHeight, districtCount, seed);
        generator.generate();

        // Print some example queries you might use for your game
        System.out.println();
        System.out.println("=== Example Queries ===");
        
        // Top 5 most desirable districts
        System.out.println("\nTop 5 Most Desirable Districts:");
        generator.getDistrictsByDesirability().stream()
                .limit(5)
                .forEach(d -> System.out.println("  " + d));

        // All commercial districts
        System.out.println("\nCommercial Districts:");
        generator.getDistrictsByType(DistrictType.COMMERCIAL).stream()
                .limit(3)
                .forEach(d -> System.out.println("  " + d));

        // Districts near city center
        Point2D center = new Point2D(cityWidth / 2, cityHeight / 2);
        System.out.println("\nDistricts within 200 units of center:");
        generator.getDistrictsNear(center, 200).stream()
                .limit(5)
                .forEach(d -> System.out.println("  " + d));

        // Display visualization
        CityVisualizer.display(generator, "Generated City (Seed: " + seed + ")");
        
        System.out.println("\n✓ Visualization window opened");
        System.out.println("  - Hover over districts for details");
        System.out.println("  - Right-click to toggle desirability heatmap");
    }
}
