package be.ragga.raggabackend.simulation.grid;

import be.ragga.raggabackend.simulation.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
}
