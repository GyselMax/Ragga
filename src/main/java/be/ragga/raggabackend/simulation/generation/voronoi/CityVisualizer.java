package be.ragga.raggabackend.simulation.generation.voronoi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.*;
import java.util.List;

/**
 * Visualizes the generated city with districts and proper road rendering.
 */
public class CityVisualizer extends JPanel {
    private final CityGenerator cityGenerator;
    private District hoveredDistrict;
    private boolean showCenters = false;
    private boolean showRoads = true;
    private boolean showDesirabilityHeatmap = false;

    // Road network cache
    private List<Road> roads;

    public CityVisualizer(CityGenerator cityGenerator) {
        this.cityGenerator = cityGenerator;

        setPreferredSize(new Dimension(
                (int) cityGenerator.getWidth(),
                (int) cityGenerator.getHeight()
        ));

        setBackground(new Color(240, 240, 240));

        // Calculate road network
        calculateRoadNetwork();

        // Add mouse listener for tooltips
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoveredDistrict(e.getX(), e.getY());
            }
        });

        // Add mouse listener for toggling visualizations
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) { // Right click
                    showDesirabilityHeatmap = !showDesirabilityHeatmap;
                    repaint();
                } else if (e.isControlDown()) {
                    showRoads = !showRoads;
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable antialiasing for smooth rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        List<District> districts = cityGenerator.getDistricts();

        // Draw all districts
        for (District district : districts) {
            drawDistrict(g2d, district);
        }

        // Draw road network on top
        if (showRoads) {
            drawRoadNetwork(g2d);
        }

        // Draw district centers
        if (showCenters) {
            for (District district : districts) {
                drawCenter(g2d, district);
            }
        }

        // Draw hovered district info
        if (hoveredDistrict != null) {
            drawDistrictInfo(g2d, hoveredDistrict);
        }

        // Draw legend
        drawLegend(g2d);

        // Draw instructions
        drawInstructions(g2d);
    }

    /**
     * Draw a single district (filled polygon)
     */
    private void drawDistrict(Graphics2D g2d, District district) {
        Polygon polygon = district.toPolygon();

        // Fill color
        Color fillColor;
        if (showDesirabilityHeatmap) {
            fillColor = getDesirabilityColor(district.getDesirability());
        } else {
            fillColor = district.getType().getColor();
        }

        // Highlight hovered district
        if (district == hoveredDistrict) {
            fillColor = fillColor.brighter().brighter();
        }

        g2d.setColor(fillColor);
        g2d.fill(polygon);
    }

    /**
     * Calculate the road network based on district boundaries
     */
    private void calculateRoadNetwork() {
        roads = new ArrayList<>();
        List<District> districts = cityGenerator.getDistricts();

        // Create a map of edges (shared boundaries between districts)
        Map<Edge, RoadSegment> edgeMap = new HashMap<>();

        for (District district : districts) {
            List<Point2D> vertices = district.getVertices();

            // For each edge of this district
            for (int i = 0; i < vertices.size(); i++) {
                Point2D p1 = vertices.get(i);
                Point2D p2 = vertices.get((i + 1) % vertices.size());

                // Create an edge (order-independent)
                Edge edge = new Edge(p1, p2);

                // If this edge exists, it's shared between two districts
                if (edgeMap.containsKey(edge)) {
                    RoadSegment segment = edgeMap.get(edge);
                    segment.district2 = district;
                } else {
                    RoadSegment segment = new RoadSegment(p1, p2);
                    segment.district1 = district;
                    edgeMap.put(edge, segment);
                }
            }
        }

        // Convert to road list and determine road types
        for (RoadSegment segment : edgeMap.values()) {
            if (segment.district1 != null && segment.district2 != null) {
                // Determine road width based on district types
                segment.width = calculateRoadWidth(segment.district1, segment.district2);
                roads.add(new Road(segment.p1, segment.p2, segment.width));
            }
        }
    }

    /**
     * Calculate road width based on adjacent district types
     */
    private float calculateRoadWidth(District d1, District d2) {
        // Major roads (wider) between commercial/industrial and anything
        if (isUrbanType(d1.getType()) || isUrbanType(d2.getType())) {
            return 4.0f;
        }
        // Medium roads in suburban areas
        else if (d1.getType() == DistrictType.SUBURBAN || d2.getType() == DistrictType.SUBURBAN) {
            return 2.5f;
        }
        // Small roads everywhere else
        else {
            return 1.5f;
        }
    }

    private boolean isUrbanType(DistrictType type) {
        return type == DistrictType.COMMERCIAL ||
                type == DistrictType.INDUSTRIAL ||
                type == DistrictType.MIXED_USE;
    }

    /**
     * Draw the road network
     */
    private void drawRoadNetwork(Graphics2D g2d) {
        // Draw roads in multiple passes for proper layering

        // Pass 1: Draw road base (wider, darker)
        for (Road road : roads) {
            g2d.setColor(new Color(60, 60, 60));
            g2d.setStroke(new BasicStroke(
                    road.width + 1.5f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            g2d.draw(new Line2D.Double(road.p1.x, road.p1.y, road.p2.x, road.p2.y));
        }

        // Pass 2: Draw road surface (thinner, lighter)
        for (Road road : roads) {
            // Color based on road size
            Color roadColor;
            if (road.width >= 4.0f) {
                roadColor = new Color(80, 80, 80); // Major roads darker
            } else if (road.width >= 2.5f) {
                roadColor = new Color(100, 100, 100); // Medium roads
            } else {
                roadColor = new Color(120, 120, 120); // Small roads lighter
            }

            g2d.setColor(roadColor);
            g2d.setStroke(new BasicStroke(
                    road.width,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            g2d.draw(new Line2D.Double(road.p1.x, road.p1.y, road.p2.x, road.p2.y));
        }

        // Pass 3: Draw center lines on major roads
        for (Road road : roads) {
            if (road.width >= 3.0f) {
                g2d.setColor(new Color(200, 180, 50, 150)); // Yellow dashed line
                float[] dashPattern = {10.0f, 10.0f};
                g2d.setStroke(new BasicStroke(
                        0.5f,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10.0f,
                        dashPattern,
                        0.0f
                ));
                g2d.draw(new Line2D.Double(road.p1.x, road.p1.y, road.p2.x, road.p2.y));
            }
        }
    }

    /**
     * Draw district center point
     */
    private void drawCenter(Graphics2D g2d, District district) {
        Point2D center = district.getCenter();

        g2d.setColor(new Color(0, 0, 0, 200));
        Ellipse2D.Double circle = new Ellipse2D.Double(
                center.x - 3, center.y - 3, 6, 6
        );
        g2d.fill(circle);

        g2d.setColor(new Color(255, 255, 255, 200));
        circle = new Ellipse2D.Double(center.x - 1.5, center.y - 1.5, 3, 3);
        g2d.fill(circle);
    }

    /**
     * Draw info for hovered district
     */
    private void drawDistrictInfo(Graphics2D g2d, District district) {
        String[] lines = {
                "District #" + district.getId(),
                "Type: " + district.getType().getDisplayName(),
                String.format("Desirability: %.2f", district.getDesirability()),
                String.format("Area: %.0f sq units", district.getArea()),
                String.format("Center: (%.0f, %.0f)", district.getCenter().x, district.getCenter().y)
        };

        // Background
        int padding = 10;
        int lineHeight = 18;
        int boxWidth = 250;
        int boxHeight = lines.length * lineHeight + padding * 2;

        g2d.setColor(new Color(255, 255, 255, 240));
        g2d.fillRoundRect(10, 10, boxWidth, boxHeight, 10, 10);

        g2d.setColor(new Color(0, 0, 0, 200));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(10, 10, boxWidth, boxHeight, 10, 10);

        // Text
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 13));
        g2d.drawString(lines[0], 20, 30);

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 1; i < lines.length; i++) {
            g2d.drawString(lines[i], 20, 30 + i * lineHeight);
        }
    }

    /**
     * Draw legend
     */
    private void drawLegend(Graphics2D g2d) {
        int x = (int) cityGenerator.getWidth() - 200;
        int y = 20;
        int swatchSize = 15;
        int spacing = 22;

        // Background
        g2d.setColor(new Color(255, 255, 255, 230));
        g2d.fillRoundRect(x - 10, y - 10, 190, DistrictType.values().length * spacing + 40, 10, 10);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));

        String title = showDesirabilityHeatmap ? "Desirability" : "District Types";
        g2d.drawString(title, x, y + 10);

        if (!showDesirabilityHeatmap) {
            // Draw type legend
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            y += 25;

            for (DistrictType type : DistrictType.values()) {
                // Color swatch
                g2d.setColor(type.getColor());
                g2d.fillRect(x, y, swatchSize, swatchSize);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x, y, swatchSize, swatchSize);

                // Label
                g2d.drawString(type.getDisplayName(), x + swatchSize + 8, y + 12);
                y += spacing;
            }
        } else {
            // Draw desirability gradient
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            y += 25;

            double[] values = {0.1, 0.5, 1.0, 1.5, 2.0};
            for (double value : values) {
                g2d.setColor(getDesirabilityColor(value));
                g2d.fillRect(x, y, swatchSize, swatchSize);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x, y, swatchSize, swatchSize);
                g2d.drawString(String.format("%.1f", value), x + swatchSize + 8, y + 12);
                y += spacing;
            }
        }
    }

    /**
     * Draw instructions
     */
    private void drawInstructions(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 230));
        g2d.fillRoundRect(10, (int) cityGenerator.getHeight() - 80, 280, 70, 10, 10);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.drawString("Hover: District info", 20, (int) cityGenerator.getHeight() - 58);
        g2d.drawString("Right-click: Toggle heatmap", 20, (int) cityGenerator.getHeight() - 40);
        g2d.drawString("Ctrl+Click: Toggle roads", 20, (int) cityGenerator.getHeight() - 22);
    }

    /**
     * Get color based on desirability (0.0 - 2.0)
     */
    private Color getDesirabilityColor(double desirability) {
        // Map 0.0-2.0 to a color gradient
        // Low (red) -> Medium (yellow) -> High (green)
        float normalized = (float) Math.min(Math.max(desirability / 2.0, 0.0), 1.0);

        if (normalized < 0.5) {
            // Red to Yellow
            float ratio = normalized * 2.0f;
            return new Color(1.0f, ratio, 0.0f);
        } else {
            // Yellow to Green
            float ratio = (normalized - 0.5f) * 2.0f;
            return new Color(1.0f - ratio, 1.0f, 0.0f);
        }
    }

    /**
     * Update which district is being hovered over
     */
    private void updateHoveredDistrict(int mouseX, int mouseY) {
        District newHovered = null;

        for (District district : cityGenerator.getDistricts()) {
            if (district.toPolygon().contains(mouseX, mouseY)) {
                newHovered = district;
                break;
            }
        }

        if (newHovered != hoveredDistrict) {
            hoveredDistrict = newHovered;
            repaint();
        }
    }

    /**
     * Display the city in a window
     */
    public static void display(CityGenerator cityGenerator, String title) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new CityVisualizer(cityGenerator));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    // Helper classes for road network

    private static class Road {
        Point2D p1, p2;
        float width;

        Road(Point2D p1, Point2D p2, float width) {
            this.p1 = p1;
            this.p2 = p2;
            this.width = width;
        }
    }

    private static class RoadSegment {
        Point2D p1, p2;
        District district1, district2;
        float width;

        RoadSegment(Point2D p1, Point2D p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    private static class Edge {
        Point2D p1, p2;

        Edge(Point2D p1, Point2D p2) {
            // Normalize order for consistent hashing
            if (p1.x < p2.x || (p1.x == p2.x && p1.y < p2.y)) {
                this.p1 = p1;
                this.p2 = p2;
            } else {
                this.p1 = p2;
                this.p2 = p1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Edge)) return false;
            Edge e = (Edge) o;
            return p1.equals(e.p1) && p2.equals(e.p2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(p1, p2);
        }
    }
}