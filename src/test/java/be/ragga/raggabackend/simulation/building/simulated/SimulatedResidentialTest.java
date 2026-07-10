package be.ragga.raggabackend.simulation.building.simulated;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Locks in the bedroomsPerHousehold derivation - clamp(floorArea / capacity /
 * 4 tiles-per-bedroom, 1, 8) with floorArea = sizeX x sizeY x floors - and the
 * constructor guards. Plain JUnit, no Spring: these are golden values for the
 * current heuristic; if TILES_PER_BEDROOM, the bedroom cap, or the formula
 * changes deliberately, update the expectations in the same commit.
 */
class SimulatedResidentialTest {

    @Test
    void familyHouseGetsTwoBedrooms() {
        // 2x2 house over 2 floors, 1 household: 8 floor tiles / 4 = 2 bedrooms.
        assertEquals(2, new SimulatedLowRise(2, 2, 2, 1).getBedroomsPerHousehold());
    }

    @Test
    void largerHouseGetsMoreBedrooms() {
        // 2x3 house over 2 floors, 1 household: 12 / 4 = 3 bedrooms.
        assertEquals(3, new SimulatedLowRise(2, 3, 2, 1).getBedroomsPerHousehold());
    }

    @Test
    void denseTowerUnitsStillGetTwoBedrooms() {
        // 6x6 tower, 15 floors, 60 households: 540/60 = 9 tiles each -> 2
        // bedrooms. Floors are what keep tower units livable: footprint-only
        // area would have floored this at the 1-bedroom minimum.
        assertEquals(2, new SimulatedHighRise(6, 6, 15, 60).getBedroomsPerHousehold());
    }

    @Test
    void crampedUnitsAreFlooredAtOneBedroom() {
        // 2x2 single floor split by 2 households: 2 tiles each, under one
        // bedroom's worth - the floor guarantees every household 1 bedroom.
        assertEquals(1, new SimulatedLowRise(2, 2, 1, 2).getBedroomsPerHousehold());
    }

    @Test
    void mansionsAreCappedAtEightBedrooms() {
        // 10x10 estate over 3 floors, 1 household: 300/4 = 75 uncapped - the
        // cap keeps single-family palaces at a sane 8 bedrooms.
        assertEquals(8, new SimulatedLowRise(10, 10, 3, 1).getBedroomsPerHousehold());
    }

    @Test
    void rotationDoesNotChangeBedrooms() {
        // The effective footprint swaps extents on 90/270-degree placement;
        // floor area (and therefore bedrooms) must be identical either way.
        assertEquals(new SimulatedLowRise(3, 4, 5, 5).getBedroomsPerHousehold(),
                new SimulatedLowRise(4, 3, 5, 5).getBedroomsPerHousehold());
    }

    @Test
    void capacityAndFloorsAreCopiedThrough() {
        SimulatedHighRise tower = new SimulatedHighRise(6, 6, 15, 60);
        assertEquals(60, tower.getHouseholdCapacity());
        assertEquals(15, tower.getFloors());
    }

    @Test
    void zeroCapacityFailsLoudly() {
        // A DB-edited RESIDENTIAL template row with capacity 0 (e.g. the
        // ddl-auto=update backfill default) must be a clear error, not an
        // ArithmeticException deep inside generation.
        assertThrows(IllegalArgumentException.class, () -> new SimulatedLowRise(2, 2, 2, 0));
    }

    @Test
    void zeroFloorsFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedLowRise(2, 2, 0, 1));
    }
}
