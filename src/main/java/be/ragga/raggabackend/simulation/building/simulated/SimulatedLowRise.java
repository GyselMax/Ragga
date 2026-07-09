package be.ragga.raggabackend.simulation.building.simulated;

import jakarta.persistence.Entity;

@Entity
public class SimulatedLowRise extends SimulatedResidential {

    protected SimulatedLowRise() {
    }

    public SimulatedLowRise(int sizeX, int sizeY, int floors, int householdCapacity) {
        super(sizeX, sizeY, floors, householdCapacity);
    }
}
