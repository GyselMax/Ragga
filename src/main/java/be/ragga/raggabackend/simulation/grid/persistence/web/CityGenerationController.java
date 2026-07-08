package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.persistence.CityPersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP entry points for generating and reading stored cities. Swagger UI is
 * wired project-wide, so these show up at /swagger-ui/index.html.
 */
@RestController
@RequestMapping("/cities")
public class CityGenerationController {

    private final CityPersistenceService service;

    public CityGenerationController(CityPersistenceService service) {
        this.service = service;
    }

    /**
     * Generates a city and stores it. Pass {@code seed} to reproduce a specific
     * map; omit it for a fresh random city (the seed is returned so the result
     * can be revisited).
     */
    @PostMapping("/generate")
    public CitySummary generate(@RequestParam(required = false) Long seed) {
        City city = service.generateAndSave(seed);
        return service.getSummary(city.getId()).orElseThrow();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CitySummary> get(@PathVariable long id) {
        return service.getSummary(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
