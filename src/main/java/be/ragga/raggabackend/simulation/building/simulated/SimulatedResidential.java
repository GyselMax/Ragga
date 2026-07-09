package be.ragga.raggabackend.simulation.building.simulated;

import jakarta.persistence.Entity;

@Entity
public abstract class SimulatedResidential extends SimulatedBuilding {

    // A residential template with at least this many floors is a high-rise
    // (SimulatedHighRise); below it, a low-rise. Roughly matches real-world
    // building codes, where ~7 stories is the usual high-rise cutoff.
    public static final int HIGH_RISE_MIN_FLOORS = 7;

    // A bedroom claims roughly this many tiles of a household's share of the
    // building's FLOOR AREA (footprint x floors), once common space, kitchen,
    // etc. are accounted for. Starting heuristic - revisit with real
    // floor-plan data.
    private static final int TILES_PER_BEDROOM = 4;

    // Even a sprawling single-family estate tops out at this many bedrooms -
    // without it the mansions' huge floor area over one household produces
    // absurd counts (30+).
    private static final int MAX_BEDROOMS_PER_HOUSEHOLD = 8;

    // Number of households this building holds, copied from its template at
    // creation time so household generation can read it directly off the
    // Building without joining through PlacedBuilding -> BuildingTemplate.
    private int householdCapacity;

    // Derived from the building's own floor area split across its households
    // - not authored on the template directly.
    private int bedroomsPerHousehold;

    protected SimulatedResidential() {
    }

    protected SimulatedResidential(int sizeX, int sizeY, int floors, int householdCapacity) {
        super(sizeX, sizeY, floors);
        // BuildingTemplate is a DB-editable catalog (grows without redeploys),
        // so a bad row is a real future failure mode, not just a theoretical
        // one - fail loudly here instead of a cryptic ArithmeticException or
        // a silently flat/bedroom-less building.
        if (householdCapacity <= 0) {
            throw new IllegalArgumentException(
                    "householdCapacity must be positive for a residential building, was " + householdCapacity);
        }
        if (floors <= 0) {
            throw new IllegalArgumentException(
                    "floors must be positive for a residential building, was " + floors);
        }
        this.householdCapacity = householdCapacity;
        int floorArea = sizeX * sizeY * floors;
        this.bedroomsPerHousehold = Math.clamp(
                floorArea / householdCapacity / TILES_PER_BEDROOM, 1, MAX_BEDROOMS_PER_HOUSEHOLD);
    }

    public int getHouseholdCapacity() {
        return householdCapacity;
    }

    public int getBedroomsPerHousehold() {
        return bedroomsPerHousehold;
    }
}
