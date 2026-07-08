package be.ragga.raggabackend.simulation.grid.persistence.repository;

import be.ragga.raggabackend.simulation.grid.persistence.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, Long> {
}
