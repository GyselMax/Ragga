package be.ragga.raggabackend.simulation.grid.persistence.repository;

import be.ragga.raggabackend.simulation.grid.persistence.entity.PlacedBuilding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacedBuildingRepository extends JpaRepository<PlacedBuilding, Long> {
}
