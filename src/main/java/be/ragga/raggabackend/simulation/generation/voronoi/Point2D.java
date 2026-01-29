package be.ragga.raggabackend.simulation.generation.voronoi;

import java.util.Objects;

/**
 * Simple 2D point with distance calculations.
 * Used for voronoi site positions and geometric operations.
 */
public class Point2D {
    public final double x, y;

    public Point2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Euclidean distance to another point
     */
    public double distanceTo(Point2D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Distance from center of a given area
     */
    public double distanceFromCenter(double width, double height) {
        Point2D center = new Point2D(width / 2.0, height / 2.0);
        return distanceTo(center);
    }

    /**
     * Normalized distance from center (0.0 = center, 1.0 = edge)
     */
    public double normalizedDistanceFromCenter(double width, double height) {
        double maxDistance = Math.sqrt(width * width + height * height) / 2.0;
        return distanceFromCenter(width, height) / maxDistance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point2D)) return false;
        Point2D point2D = (Point2D) o;
        return Double.compare(point2D.x, x) == 0 && 
               Double.compare(point2D.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}
