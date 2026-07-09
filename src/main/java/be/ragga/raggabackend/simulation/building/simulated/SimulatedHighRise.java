package be.ragga.raggabackend.simulation.building.simulated;

import jakarta.persistence.Entity;

@Entity
public class SimulatedHighRise extends SimulatedResidential {

    protected SimulatedHighRise() {
    }

    public SimulatedHighRise(int sizeX, int sizeY, int floors, int householdCapacity) {
        super(sizeX, sizeY, floors, householdCapacity);
    }
}
