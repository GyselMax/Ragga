package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.persistence.CityPersistenceService;
import org.springframework.http.MediaType;
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

    /**
     * Renders a stored city as a PNG for eyeballing the map - the API twin of
     * the standalone GridVisualizer (same color legend, see GENERATION.md).
     * {@code cellSize} is pixels per tile (clamped 1-10): 500x500 at the
     * default 4 gives a 2000x2000 image. Swagger UI shows the image inline.
     */
    @GetMapping(value = "/{id}/render", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> render(@PathVariable long id,
                                         @RequestParam(defaultValue = "4") int cellSize) {
        return service.renderPng(id, cellSize)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
