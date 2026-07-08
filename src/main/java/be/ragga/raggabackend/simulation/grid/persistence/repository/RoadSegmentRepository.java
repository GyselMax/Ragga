package be.ragga.raggabackend.simulation.grid.persistence.repository;

import be.ragga.raggabackend.simulation.grid.persistence.entity.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long> {
}
