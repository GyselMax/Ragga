package be.ragga.raggabackend.simulation.building.simulated;

import be.ragga.raggabackend.simulation.building.Building;
import jakarta.persistence.Entity;

@Entity
public abstract class SimulatedBuilding extends Building {

    protected SimulatedBuilding() {
    }

    protected SimulatedBuilding(int sizeX, int sizeY, int floors) {
        super(sizeX, sizeY, floors);
    }
}
