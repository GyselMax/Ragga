package be.ragga.raggabackend.simulation.generation.voronoi;

import java.util.*;

/**
 * Generates a complete voronoi diagram with no gaps.
 * Uses a pixel-based approach to ensure every point belongs to exactly one district.
 */
public class VoronoiDiagram {
    private final double width;
    private final double height;
    private final List<District> districts;
    private final Random random;
    private int[][] districtMap; // Maps every pixel to a district ID

    public VoronoiDiagram(double width, double height, Random random) {
        this.width = width;
        this.height = height;
        this.random = random;
        this.districts = new ArrayList<>();
        this.districtMap = new int[(int) width][(int) height];
    }

    /**
     * Generate voronoi sites with density decreasing from center
     */
    public void generateSites(int targetDistrictCount) {
        districts.clear();

        Point2D center = new Point2D(width / 2.0, height / 2.0);
        double maxRadius = Math.sqrt(width * width + height * height) / 2.0;

        // Generate points with higher density toward center
        int siteCount = 0;
        while (siteCount < targetDistrictCount) {
            // Use rejection sampling to bias toward center
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            Point2D candidate = new Point2D(x, y);

            // Distance from center (0.0 to 1.0)
            double normalizedDistance = candidate.distanceTo(center) / maxRadius;

            // Accept with probability inversely proportional to distance
            // Center points have ~90% acceptance, edge points have ~10%
            double acceptanceProbability = 1.0 - (normalizedDistance * 0.8);

            if (random.nextDouble() < acceptanceProbability) {
                District district = new District(siteCount, candidate);
                districts.add(district);
                siteCount++;
            }
        }
    }

    /**
     * Calculate voronoi cells by assigning every pixel to its nearest site.
     * This ensures NO GAPS in the diagram.
     */
    public void calculateVoronoiCells() {
        int w = (int) width;
        int h = (int) height;

        // Assign every pixel to its closest district
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Point2D pixel = new Point2D(x, y);
                District closest = findClosestDistrict(pixel);
                districtMap[x][y] = closest.getId();
            }
        }

        // Now trace the boundaries of each district from the pixel map
        for (District district : districts) {
            List<Point2D> boundary = traceBoundary(district.getId());
            district.setVertices(boundary);
        }
    }

    /**
     * Find the closest district to a point
     */
    private District findClosestDistrict(Point2D point) {
        District closest = districts.get(0);
        double minDistance = point.distanceTo(closest.getCenter());

        for (int i = 1; i < districts.size(); i++) {
            District district = districts.get(i);
            double distance = point.distanceTo(district.getCenter());
            if (distance < minDistance) {
                minDistance = distance;
                closest = district;
            }
        }

        return closest;
    }

    /**
     * Trace the boundary of a district using marching squares algorithm.
     * This gives us a smooth polygon boundary.
     */
    private List<Point2D> traceBoundary(int districtId) {
        Set<Point2D> boundaryPixels = new HashSet<>();

        int w = (int) width;
        int h = (int) height;

        // Find all boundary pixels (pixels that touch a different district)
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (districtMap[x][y] == districtId) {
                    // Check if this pixel is on the boundary
                    if (isBoundaryPixel(x, y, districtId)) {
                        boundaryPixels.add(new Point2D(x, y));
                    }
                }
            }
        }

        // If no boundary found (shouldn't happen), create a small square
        if (boundaryPixels.isEmpty()) {
            District district = districts.stream()
                    .filter(d -> d.getId() == districtId)
                    .findFirst()
                    .orElse(null);

            if (district != null) {
                Point2D center = district.getCenter();
                boundaryPixels.add(new Point2D(center.x - 5, center.y - 5));
                boundaryPixels.add(new Point2D(center.x + 5, center.y - 5));
                boundaryPixels.add(new Point2D(center.x + 5, center.y + 5));
                boundaryPixels.add(new Point2D(center.x - 5, center.y + 5));
            }
        }

        // Simplify the boundary to reduce polygon complexity
        return simplifyBoundary(new ArrayList<>(boundaryPixels));
    }

    /**
     * Check if a pixel is on the boundary (touches different district)
     */
    private boolean isBoundaryPixel(int x, int y, int districtId) {
        int w = (int) width;
        int h = (int) height;

        // Check 4-connected neighbors
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                return true; // Edge of map
            }

            if (districtMap[nx][ny] != districtId) {
                return true; // Different district
            }
        }

        return false;
    }

    /**
     * Simplify boundary by taking every Nth point in a convex hull ordering
     */
    private List<Point2D> simplifyBoundary(List<Point2D> boundaryPixels) {
        if (boundaryPixels.size() <= 8) {
            return convexHull(boundaryPixels);
        }

        // Compute convex hull for simplified boundary
        List<Point2D> hull = convexHull(boundaryPixels);

        // If hull is still complex, subsample it
        if (hull.size() > 32) {
            List<Point2D> simplified = new ArrayList<>();
            int step = hull.size() / 32;
            for (int i = 0; i < hull.size(); i += step) {
                simplified.add(hull.get(i));
            }
            return simplified;
        }

        return hull;
    }

    /**
     * Compute convex hull using Graham scan
     */
    private List<Point2D> convexHull(List<Point2D> points) {
        if (points.size() <= 3) {
            return new ArrayList<>(points);
        }

        // Find the point with lowest y (and leftmost if tie)
        Point2D pivot = points.stream()
                .min((p1, p2) -> {
                    int cmp = Double.compare(p1.y, p2.y);
                    return cmp != 0 ? cmp : Double.compare(p1.x, p2.x);
                })
                .get();

        // Sort points by polar angle with respect to pivot
        List<Point2D> sorted = new ArrayList<>(points);
        sorted.remove(pivot);
        sorted.sort((p1, p2) -> {
            double angle1 = Math.atan2(p1.y - pivot.y, p1.x - pivot.x);
            double angle2 = Math.atan2(p2.y - pivot.y, p2.x - pivot.x);
            return Double.compare(angle1, angle2);
        });

        // Graham scan
        Stack<Point2D> hull = new Stack<>();
        hull.push(pivot);

        for (Point2D p : sorted) {
            while (hull.size() > 1 && ccw(hull.get(hull.size() - 2), hull.peek(), p) <= 0) {
                hull.pop();
            }
            hull.push(p);
        }

        return new ArrayList<>(hull);
    }

    /**
     * Counter-clockwise test
     */
    private double ccw(Point2D a, Point2D b, Point2D c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    /**
     * Get the district ID at a specific pixel location
     */
    public int getDistrictIdAt(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return districtMap[x][y];
        }
        return -1;
    }

    public List<District> getDistricts() {
        return new ArrayList<>(districts);
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }
}