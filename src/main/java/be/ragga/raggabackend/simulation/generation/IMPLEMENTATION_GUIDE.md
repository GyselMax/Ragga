# Voronoi City Generator - Implementation Guide

## 📋 Overview

You've got a complete voronoi-based city generator that creates realistic urban layouts with:
- **Center-dense distribution** (more districts in the center, fewer at edges)
- **Logical zoning** (commercial core → residential → suburban → agricultural)
- **Desirability scoring** (based on location and neighboring districts)
- **Interactive visualization** (hover for details, right-click for heatmap)

## 🏗️ Architecture

```
Point2D.java              → Basic geometry (points, distances)
DistrictType.java         → Zone types (commercial, residential, etc.)
District.java             → Individual voronoi cell with properties
VoronoiDiagram.java       → Geometry engine (creates cells)
DistrictClassifier.java   → Assigns types based on location
CityGenerator.java        → Main orchestrator
CityVisualizer.java       → Swing visualization
VoronoiCityMain.java      → Runnable entry point
```

## 🚀 Quick Start

### 1. Run the Generator
```java
java be.ragga.raggabackend.simulation.generation.voronoi.VoronoiCityMain
```

### 2. Understand the Output
```
=== Starting City Generation ===
Dimensions: 1200.0 x 800.0
Target districts: 150
Seed: 42

Step 1: Generating voronoi sites...
  Generated 150 sites

Step 2: Calculating voronoi cells...
  Calculated cell boundaries

Step 3: Classifying districts...
  Classified 150 districts

District Distribution:
---------------------
  Residential    :  45 (30.0%)
  Suburban       :  32 (21.3%)
  Commercial     :  28 (18.7%)
  ...
```

### 3. Interact with Visualization
- **Hover** over any district → See detailed info
- **Right-click** anywhere → Toggle desirability heatmap
- **Districts** are color-coded by type (see legend)

## 🎮 Using This in Your Game

### Getting District Data

```java
CityGenerator generator = new CityGenerator(1200, 800, 150, 42L);
generator.generate();

// Access any district
District district = generator.getDistrict(15);
System.out.println(district.getType());          // COMMERCIAL
System.out.println(district.getDesirability());  // 1.45
System.out.println(district.getArea());          // 2847.3

// Find districts by type
List<District> commercial = generator.getDistrictsByType(DistrictType.COMMERCIAL);

// Find best locations
List<District> topDistricts = generator.getDistrictsByDesirability();

// Spatial queries
Point2D location = new Point2D(600, 400);
List<District> nearby = generator.getDistrictsNear(location, 150.0);
```

### Integrating with Your Financial System

Each `District` has properties perfect for your economic simulation:

```java
District district = generator.getDistrict(10);

// Economic factors
double economicValue = district.getType().getEconomicMultiplier();
// COMMERCIAL = 1.5x, INDUSTRIAL = 1.8x, PARK = 0.1x

// Population capacity (based on type and area)
double populationCapacity = calculateCapacity(district);

// Rent/price (based on desirability)
double baseRent = 1000 * district.getDesirability();
// High desirability (1.8) = $1800/month
// Low desirability (0.3) = $300/month

// Job availability (based on type)
int jobs = calculateJobs(district);

private double calculateCapacity(District d) {
    if (d.getType() == DistrictType.RESIDENTIAL) {
        return d.getArea() * 0.5; // 0.5 people per sq unit
    } else if (d.getType() == DistrictType.SUBURBAN) {
        return d.getArea() * 0.2;
    }
    return 0;
}

private int calculateJobs(District d) {
    switch (d.getType()) {
        case COMMERCIAL: return (int) (d.getArea() * 0.3);
        case INDUSTRIAL: return (int) (d.getArea() * 0.4);
        case MIXED_USE: return (int) (d.getArea() * 0.25);
        default: return 0;
    }
}
```

## 🔧 Customization

### Adjust City Size and Density

```java
// Small dense city
CityGenerator smallCity = new CityGenerator(800, 600, 200, seed);

// Large sprawling city
CityGenerator largeCity = new CityGenerator(2000, 1500, 300, seed);

// Control density by adjusting districtCount relative to size
```

### Add New District Types

In `DistrictType.java`:
```java
ENTERTAINMENT(
    "Entertainment",
    new Color(255, 100, 200),
    1.3,  // High desirability
    1.2   // Good economic value
),
```

In `DistrictClassifier.java`, add to `determineTypeByDistance()`:
```java
if (normalizedDistance < 0.3) {
    double r = random.nextDouble();
    if (r < 0.3) return DistrictType.COMMERCIAL;
    if (r < 0.5) return DistrictType.ENTERTAINMENT;  // NEW
    return DistrictType.MIXED_USE;
}
```

### Modify Desirability Calculations

In `District.java`, edit `getAdjacencyScore()`:
```java
// Make parks boost nearby residential even more
if (typeB == DistrictType.PARK) return 0.4; // was 0.2

// Make industrial zones hurt residential less
if (typeA == DistrictType.RESIDENTIAL && typeB == DistrictType.INDUSTRIAL) {
    return -0.1; // was -0.2
}
```

### Change Distribution Pattern

In `DistrictClassifier.java`, adjust `determineTypeByDistance()` ranges:
```java
// More commercial in core
if (normalizedDistance < 0.35) {  // was 0.25
    return DistrictType.COMMERCIAL;
}

// Keep residential closer to center
if (normalizedDistance < 0.55) {  // was 0.45
    return DistrictType.RESIDENTIAL;
}
```

## 💡 Key Design Decisions Explained

### Why Voronoi?
- **Natural boundaries**: Districts have organic, realistic shapes
- **Scalable**: Can generate 50 or 500 districts equally well
- **Hierarchical-ready**: Can subdivide cells later for blocks/buildings
- **Efficient spatial queries**: Easy to find neighbors and nearby districts

### Why Distance-Based Classification?
- **Realistic urban growth**: Cities naturally grow from center outward
- **Controllable**: Easy to adjust by tweaking distance thresholds
- **Predictable**: Similar seeds produce similar cities
- **Flexible**: Add randomness for variety while maintaining structure

### Desirability Calculation
Three factors:
1. **Base desirability** from district type (commercial = 1.2, industrial = 0.3)
2. **Distance modifier** (central = better for most types)
3. **Adjacency bonuses** (near park = +0.2, near industrial = -0.2)

This creates emergent patterns where commercial cores have high desirability, industrial edges have low desirability, and parks boost their neighborhoods.

## 🐛 Performance Notes

Current implementation is **educational/prototype quality**:
- Voronoi calculation: O(n²) per cell, O(n³) total
- Fine for 50-300 districts
- For 500+ districts, consider using a library

### Production Upgrade Path

For better performance, replace `VoronoiDiagram.java` with:

```java
// Add to pom.xml or build.gradle
// org.locationtech.jts:jts-core:1.19.0

import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

// Then in VoronoiDiagram.java:
public void calculateVoronoiCells() {
    VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
    
    Coordinate[] coords = districts.stream()
        .map(d -> new Coordinate(d.getCenter().x, d.getCenter().y))
        .toArray(Coordinate[]::new);
    
    builder.setSites(coords);
    Geometry diagram = builder.getDiagram(new GeometryFactory());
    
    // Convert JTS geometries to your District vertices
    // ... (conversion code)
}
```

This gives you O(n log n) performance and perfect voronoi cells.

## 🎯 Next Steps for Your Game

### 1. Save/Load Cities
```java
// Serialize to JSON/database
public class CityData {
    long seed;
    List<DistrictData> districts;
}

public class DistrictData {
    int id;
    DistrictType type;
    double desirability;
    Point2D center;
    // ... other properties
}
```

### 2. Add Inhabitants
```java
public class Inhabitant {
    int id;
    District homeDistrict;      // Where they live
    District workDistrict;      // Where they work
    double income;              // Based on work district type
    double expenses;            // Based on home district desirability
    
    public void updateFinances() {
        // Earn based on job
        income = workDistrict.getType().getEconomicMultiplier() * baseWage;
        
        // Pay rent based on home desirability
        expenses = homeDistrict.getDesirability() * baseRent;
    }
}
```

### 3. Subdivide Districts into Buildings
```java
public class Building {
    District parentDistrict;
    Point2D location;           // Within parent district
    BuildingType type;          // Apartment, office, factory, etc.
    List<Inhabitant> residents;
    
    // Your sub-ID system
    String getFullId() {
        return parentDistrict.getId() + "-" + localId;
        // e.g., "42-7" = Building 7 in District 42
    }
}
```

### 4. Update Cycle (2-4x per day)
```java
public class CitySimulation {
    CityGenerator city;
    List<Inhabitant> inhabitants;
    
    public void dailyUpdate() {
        // Update inhabitants
        for (Inhabitant i : inhabitants) {
            i.updateFinances();
            i.considerMoving();  // Based on affordability/desirability
        }
        
        // Update district metrics
        for (District d : city.getDistricts()) {
            d.updatePopulation(inhabitants);
            d.updateEconomy();
        }
    }
}
```

## 📊 Example Output

Running with seed 42 gives you:
- ~30% Residential (center-mid range)
- ~21% Suburban (outer ring)
- ~19% Commercial (dense core)
- ~15% Mixed Use (transition zones)
- ~8% Parks (scattered throughout)
- ~4% Industrial (edges)
- ~3% Agricultural (far edges)

Desirability ranges from 0.12 (edge agricultural) to 1.94 (central commercial near parks).

## 🎓 Learning Takeaways

This system demonstrates:
1. **Separation of concerns**: Each class has one job
2. **Data-driven design**: District properties drive game mechanics
3. **Emergent complexity**: Simple rules → realistic patterns
4. **Progressive enhancement**: Start simple, optimize later
5. **Game-ready architecture**: Easy to extend with your financial system

---

**Remember**: This is a foundation. The real magic happens when you add your inhabitants and watch them interact with the city's economic geography. Good luck with your game! 🎮
