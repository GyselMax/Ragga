package be.ragga.raggabackend.simulation.generation.voronoi;

import java.awt.Polygon;
import java.util.*;

/**
 * Represents one district in the city.
 * Each district is one cell in the voronoi diagram.
 */
public class District {
    private final int id;
    private final Point2D center;
    private final List<Point2D> vertices;
    private DistrictType type;
    private double desirability;
    private double area;

    public District(int id, Point2D center) {
        this.id = id;
        this.center = center;
        this.vertices = new ArrayList<>();
        this.type = DistrictType.UNDEVELOPED; // Default until classified
        this.desirability = 0.0;
        this.area = 0.0;
    }

    /**
     * Set the vertices of this district's polygon
     */
    public void setVertices(List<Point2D> vertices) {
        this.vertices.clear();
        this.vertices.addAll(vertices);
        this.area = calculateArea();
    }

    /**
     * Calculate polygon area using shoelace formula
     */
    private double calculateArea() {
        if (vertices.size() < 3) return 0.0;

        double sum = 0.0;
        for (int i = 0; i < vertices.size(); i++) {
            Point2D current = vertices.get(i);
            Point2D next = vertices.get((i + 1) % vertices.size());
            sum += current.x * next.y - next.x * current.y;
        }
        return Math.abs(sum) / 2.0;
    }

    /**
     * Calculate final desirability based on:
     * - District type base desirability
     * - Distance from city center (closer = better for most types)
     * - Adjacent district bonuses
     */
    public void calculateDesirability(double cityWidth, double cityHeight, List<District> allDistricts) {
        // Start with base desirability from type
        desirability = type.getBaseDesirability();

        // Distance factor (0.0 = center, 1.0 = edge)
        double distanceFactor = center.normalizedDistanceFromCenter(cityWidth, cityHeight);
        
        // Most types prefer being central (except agricultural)
        if (type == DistrictType.AGRICULTURAL || type == DistrictType.UNDEVELOPED) {
            // These prefer edges
            desirability *= (0.5 + distanceFactor);
        } else {
            // Urban types prefer center
            desirability *= (1.5 - distanceFactor);
        }

        // Adjacency bonuses
        double adjacencyBonus = calculateAdjacencyBonus(allDistricts);
        desirability += adjacencyBonus;

        // Clamp to reasonable range
        desirability = Math.max(0.1, Math.min(2.0, desirability));
    }

    /**
     * Calculate bonus/penalty based on neighboring districts
     */
    private double calculateAdjacencyBonus(List<District> allDistricts) {
        double bonus = 0.0;
        int neighborCount = 0;

        // Find neighbors (districts within reasonable distance)
        double neighborThreshold = 150.0; // Adjust based on your scale

        for (District other : allDistricts) {
            if (other.id == this.id) continue;

            double distance = center.distanceTo(other.center);
            if (distance < neighborThreshold) {
                neighborCount++;
                bonus += getAdjacencyScore(this.type, other.type);
            }
        }

        return neighborCount > 0 ? bonus / neighborCount : 0.0;
    }

    /**
     * How much does typeA benefit from being near typeB?
     */
    private double getAdjacencyScore(DistrictType typeA, DistrictType typeB) {
        // Parks boost everything
        if (typeB == DistrictType.PARK) return 0.2;
        
        // Residential likes being near commercial and parks
        if (typeA == DistrictType.RESIDENTIAL) {
            if (typeB == DistrictType.COMMERCIAL || typeB == DistrictType.MIXED_USE) return 0.15;
            if (typeB == DistrictType.INDUSTRIAL) return -0.2;
        }

        // Commercial likes density
        if (typeA == DistrictType.COMMERCIAL) {
            if (typeB == DistrictType.RESIDENTIAL || typeB == DistrictType.MIXED_USE) return 0.1;
        }

        // Industrial prefers isolation from residential
        if (typeA == DistrictType.INDUSTRIAL) {
            if (typeB == DistrictType.RESIDENTIAL) return -0.1;
            if (typeB == DistrictType.INDUSTRIAL) return 0.1;
        }

        return 0.0;
    }

    /**
     * Convert to AWT Polygon for rendering
     */
    public Polygon toPolygon() {
        int[] xPoints = new int[vertices.size()];
        int[] yPoints = new int[vertices.size()];

        for (int i = 0; i < vertices.size(); i++) {
            xPoints[i] = (int) vertices.get(i).x;
            yPoints[i] = (int) vertices.get(i).y;
        }

        return new Polygon(xPoints, yPoints, vertices.size());
    }

    // Getters and setters
    public int getId() { return id; }
    public Point2D getCenter() { return center; }
    public List<Point2D> getVertices() { return new ArrayList<>(vertices); }
    public DistrictType getType() { return type; }
    public void setType(DistrictType type) { this.type = type; }
    public double getDesirability() { return desirability; }
    public double getArea() { return area; }

    @Override
    public String toString() {
        return String.format("District#%d [%s] at %s (desirability: %.2f, area: %.0f)",
                id, type.getDisplayName(), center, desirability, area);
    }
}
