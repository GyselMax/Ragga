package be.ragga.raggabackend.simulation.generation.voronoi;

import java.util.*;

/**
 * Classifies districts based on their position and characteristics.
 * Creates a realistic urban -> suburban -> rural gradient.
 */
public class DistrictClassifier {
    private final double cityWidth;
    private final double cityHeight;
    private final Random random;

    public DistrictClassifier(double cityWidth, double cityHeight, Random random) {
        this.cityWidth = cityWidth;
        this.cityHeight = cityHeight;
        this.random = random;
    }

    /**
     * Classify all districts based on distance from center and randomization
     */
    public void classifyDistricts(List<District> districts) {
        // First pass: assign base types based on distance
        for (District district : districts) {
            DistrictType type = determineTypeByDistance(district);
            district.setType(type);
        }

        // Second pass: add variety and ensure good distribution
        addVariety(districts);

        // Third pass: ensure we have some parks
        addParks(districts);

        // Fourth pass: calculate desirability scores
        for (District district : districts) {
            district.calculateDesirability(cityWidth, cityHeight, districts);
        }
    }

    /**
     * Determine district type based on distance from center
     */
    private DistrictType determineTypeByDistance(District district) {
        double normalizedDistance = district.getCenter()
                .normalizedDistanceFromCenter(cityWidth, cityHeight);

        // Add some randomness
        double rand = random.nextDouble() * 0.2;
        normalizedDistance += rand;

        // Core (0.0 - 0.25): Commercial, Mixed Use
        if (normalizedDistance < 0.25) {
            return random.nextDouble() < 0.6 ? DistrictType.COMMERCIAL : DistrictType.MIXED_USE;
        }
        // Inner city (0.25 - 0.45): Residential, Mixed Use, some Industrial
        else if (normalizedDistance < 0.45) {
            double r = random.nextDouble();
            if (r < 0.5) return DistrictType.RESIDENTIAL;
            if (r < 0.8) return DistrictType.MIXED_USE;
            return DistrictType.INDUSTRIAL;
        }
        // Suburbs (0.45 - 0.65): Mostly Residential, some Suburban
        else if (normalizedDistance < 0.65) {
            return random.nextDouble() < 0.6 ? DistrictType.RESIDENTIAL : DistrictType.SUBURBAN;
        }
        // Outer suburbs (0.65 - 0.85): Suburban, some Agricultural
        else if (normalizedDistance < 0.85) {
            return random.nextDouble() < 0.7 ? DistrictType.SUBURBAN : DistrictType.AGRICULTURAL;
        }
        // Rural (0.85+): Agricultural, Undeveloped
        else {
            return random.nextDouble() < 0.6 ? DistrictType.AGRICULTURAL : DistrictType.UNDEVELOPED;
        }
    }

    /**
     * Add variety - ensure we don't have too much of one type clustered
     */
    private void addVariety(List<District> districts) {
        // Count types
        Map<DistrictType, Integer> typeCounts = new EnumMap<>(DistrictType.class);
        for (District d : districts) {
            typeCounts.put(d.getType(), typeCounts.getOrDefault(d.getType(), 0) + 1);
        }

        // If any type dominates too much (>40%), convert some to variety
        int totalDistricts = districts.size();
        for (Map.Entry<DistrictType, Integer> entry : typeCounts.entrySet()) {
            if (entry.getValue() > totalDistricts * 0.4) {
                // Convert some to related types
                convertExcess(districts, entry.getKey());
            }
        }
    }

    /**
     * Convert some districts from an over-represented type to variety
     */
    private void convertExcess(List<District> districts, DistrictType excessType) {
        int conversionCount = 0;
        int targetConversions = (int) (districts.size() * 0.05); // Convert 5%

        for (District district : districts) {
            if (district.getType() == excessType && conversionCount < targetConversions) {
                if (random.nextDouble() < 0.3) {
                    district.setType(getRelatedType(excessType));
                    conversionCount++;
                }
            }
        }
    }

    /**
     * Get a related type for variety
     */
    private DistrictType getRelatedType(DistrictType type) {
        switch (type) {
            case RESIDENTIAL:
                return random.nextDouble() < 0.5 ? DistrictType.MIXED_USE : DistrictType.SUBURBAN;
            case COMMERCIAL:
                return DistrictType.MIXED_USE;
            case INDUSTRIAL:
                return random.nextDouble() < 0.5 ? DistrictType.MIXED_USE : DistrictType.COMMERCIAL;
            case SUBURBAN:
                return DistrictType.RESIDENTIAL;
            case AGRICULTURAL:
                return DistrictType.SUBURBAN;
            default:
                return type;
        }
    }

    /**
     * Ensure we have some parks distributed throughout
     */
    private void addParks(List<District> districts) {
        int parkCount = (int) (districts.size() * 0.08); // 8% parks
        int parksCreated = 0;

        // Prioritize converting smaller districts in mid-range areas
        List<District> candidates = new ArrayList<>(districts);
        candidates.sort(Comparator.comparingDouble(District::getArea));

        for (District district : candidates) {
            if (parksCreated >= parkCount) break;

            double normalizedDistance = district.getCenter()
                    .normalizedDistanceFromCenter(cityWidth, cityHeight);

            // Parks mainly in inner city and suburbs (0.2 - 0.7 range)
            if (normalizedDistance > 0.2 && normalizedDistance < 0.7) {
                if (district.getType() == DistrictType.RESIDENTIAL || 
                    district.getType() == DistrictType.MIXED_USE ||
                    district.getType() == DistrictType.SUBURBAN) {
                    
                    if (random.nextDouble() < 0.15) {
                        district.setType(DistrictType.PARK);
                        parksCreated++;
                    }
                }
            }
        }
    }

    /**
     * Get statistics about district distribution
     */
    public Map<DistrictType, Integer> getDistributionStats(List<District> districts) {
        Map<DistrictType, Integer> stats = new EnumMap<>(DistrictType.class);
        for (District d : districts) {
            stats.put(d.getType(), stats.getOrDefault(d.getType(), 0) + 1);
        }
        return stats;
    }
}
