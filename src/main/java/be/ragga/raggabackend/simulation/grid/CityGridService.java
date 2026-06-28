package be.ragga.raggabackend.simulation.grid;

import be.ragga.raggabackend.simulation.City;
import org.springframework.stereotype.Service;

@Service
public class CityGridService {

    private final CityGridGenerator gridGenerator;
    private final CityRepository cityRepository;

    public CityGridService(CityGridGenerator gridGenerator, CityRepository cityRepository) {
        this.gridGenerator = gridGenerator;
        this.cityRepository = cityRepository;
    }

    public City generateCity() {
        City city = new City();
        city.setGrid(gridGenerator.generate());
        return cityRepository.save(city);
    }
}
