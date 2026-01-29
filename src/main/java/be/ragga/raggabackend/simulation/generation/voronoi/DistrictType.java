package be.ragga.raggabackend.simulation.generation.voronoi;

import java.awt.Color;

/**
 * Types of districts in our city.
 * Each has different characteristics that affect desirability and economics.
 */
public enum DistrictType {
    // Core urban areas
    COMMERCIAL(
        "Commercial", 
        new Color(0, 150, 200),
        1.2,  // High base desirability
        1.5   // High economic value multiplier
    ),
    
    RESIDENTIAL(
        "Residential",
        new Color(200, 200, 200),
        1.0,  // Baseline desirability
        0.8   // Moderate economic value
    ),
    
    INDUSTRIAL(
        "Industrial",
        new Color(200, 100, 0),
        0.3,  // Low residential desirability
        1.8   // High economic output
    ),
    
    // Mid-range zones
    MIXED_USE(
        "Mixed Use",
        new Color(180, 150, 180),
        0.9,
        1.1
    ),
    
    PARK(
        "Park",
        new Color(50, 205, 50),
        1.5,  // Parks boost nearby desirability
        0.1   // Low direct economic value
    ),
    
    // Outer zones
    SUBURBAN(
        "Suburban",
        new Color(220, 220, 180),
        0.7,
        0.5
    ),
    
    AGRICULTURAL(
        "Agricultural",
        new Color(139, 195, 74),
        0.4,
        0.6
    ),
    
    // Edge zones
    UNDEVELOPED(
        "Undeveloped",
        new Color(160, 140, 120),
        0.1,
        0.0
    );

    private final String displayName;
    private final Color color;
    private final double baseDesirability;
    private final double economicMultiplier;

    DistrictType(String displayName, Color color, double baseDesirability, double economicMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.baseDesirability = baseDesirability;
        this.economicMultiplier = economicMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public Color getColor() { return color; }
    public double getBaseDesirability() { return baseDesirability; }
    public double getEconomicMultiplier() { return economicMultiplier; }
}
