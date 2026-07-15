package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.economy.BusinessEconomyConfig;
import be.ragga.raggabackend.simulation.economy.EconomyConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.persistence.CityPersistenceService;
import be.ragga.raggabackend.simulation.grid.persistence.CityPreviewService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * HTTP entry points for generating and reading stored cities. Swagger UI is
 * wired project-wide, so these show up at /swagger-ui/index.html.
 *
 * The {@code /preview/**} endpoints back the interactive tuner page at
 * {@code /tuner.html}: they generate + render in memory without persisting, so
 * knobs can be swept freely without filling the DB.
 */
// Allow the tuner page to call these endpoints when it is opened from a
// different local origin than the app - e.g. IntelliJ's built-in file server on
// :63342, or a file:// path. Scoped to localhost so only the developer's own
// machine (never a remote site) can reach the API cross-origin.
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
@RestController
@RequestMapping("/cities")
public class CityGenerationController {

    // Saving stores one GridCell row per tile; beyond this per-side size the
    // row count (millions) makes persistence impractical. Preview is unaffected.
    private static final int MAX_PERSIST_DIM = 1000;

    private final CityPersistenceService service;
    private final CityPreviewService previewService;

    public CityGenerationController(CityPersistenceService service, CityPreviewService previewService) {
        this.service = service;
        this.previewService = previewService;
    }

    /**
     * Generates a city from the given knobs and stores it - the persisting twin
     * of {@code /preview/render}, so a city tuned in {@code /tuner.html} can be
     * saved exactly as previewed. Every {@link GenerationParams} knob is
     * accepted (omitted ones keep their default); {@code seed} makes it
     * reproducible, or is randomized (and returned) if omitted. An impossible
     * knob combination comes back as a 400 with the reason.
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@ModelAttribute GenerationParams params,
                                      @ModelAttribute EconomyParams economyParams,
                                      @ModelAttribute BusinessParams businessParams,
                                      @RequestParam(required = false) Long seed) {
        long actualSeed = seed != null ? seed : new Random().nextLong();
        GenerationConfig config;
        EconomyConfig economy;
        BusinessEconomyConfig business;
        try {
            config = params.toConfig();
            // Overlay the tuner's economy sliders on the calibrated defaults; each
            // record's compact constructor validates the combination.
            economy = economyParams.toConfig(EconomyConfig.defaults());
            business = businessParams.toConfig(BusinessEconomyConfig.defaults());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("invalid config: " + e.getMessage());
        }
        // Persisting stores one row per tile, so a big map is millions of rows.
        // Preview happily renders larger maps in memory; saving one does not.
        if (config.width() > MAX_PERSIST_DIM || config.height() > MAX_PERSIST_DIM) {
            return ResponseEntity.badRequest().body(
                    "map too large to persist (" + config.width() + "x" + config.height()
                            + "); saving is limited to " + MAX_PERSIST_DIM + " tiles per side ("
                            + "one row per tile). Preview supports larger maps.");
        }
        City city = service.generateAndSave(config, economy, business, actualSeed);
        return ResponseEntity.ok(service.getSummary(city.getId()).orElseThrow());
    }

    /** Every stored city, so you can find the id of one to view. */
    @GetMapping
    public List<CitySummary> list() {
        return service.listSummaries();
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
     * default 4 gives a 2000x2000 image. {@code showFloors} overlays each
     * building's floor count as a visual aid. Swagger UI shows the image inline.
     */
    @GetMapping(value = "/{id}/render", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> render(@PathVariable long id,
                                         @RequestParam(defaultValue = "4") int cellSize,
                                         @RequestParam(defaultValue = "false") boolean showFloors) {
        return service.renderPng(id, cellSize, showFloors)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Renders a stored city's residential economy as a PNG heatmap - the API
     * twin of the standalone EconomyVisualizer. {@code mode=value} (default)
     * colours each home by its cleared per-dwelling value on a cold-to-hot
     * ramp; {@code mode=tenure} colours it owner-occupied / rented / vacant.
     * {@code cellSize} is px per tile (clamped 1-10). Swagger UI shows the image
     * inline. Only meaningful for a city generated after the economy pass
     * existed; older cities render as unpriced/vacant.
     */
    @GetMapping(value = "/{id}/heatmap", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> heatmap(@PathVariable long id,
                                     @RequestParam(defaultValue = "value") String mode,
                                     @RequestParam(defaultValue = "4") int cellSize) {
        EconomyHeatmapRenderer.Mode parsed;
        try {
            parsed = EconomyHeatmapRenderer.Mode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("unknown heatmap mode '" + mode + "'; use 'value' or 'tenure'")
                            .getBytes(StandardCharsets.UTF_8));
        }
        return service.renderEconomyHeatmap(id, cellSize, parsed)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The default {@link GenerationConfig} as JSON. The tuner page fetches this
     * to build its form (one input per knob, pre-filled) and to know each
     * param's name and type, so the page never hardcodes the knob list.
     */
    @GetMapping("/preview/defaults")
    public GenerationConfig previewDefaults() {
        return GenerationConfig.defaults(400, 400);
    }

    /**
     * The default {@link EconomyConfig} as JSON. The tuner page fetches this to
     * build its Economy section (one input per knob, pre-filled) the same way it
     * builds the generation form from {@code /preview/defaults}. The economy is
     * applied only on the persisting {@code /generate} path - the in-memory map
     * preview doesn't run the market pass - so there is no economy preview render.
     */
    @GetMapping("/economy/defaults")
    public EconomyConfig economyDefaults() {
        return EconomyConfig.defaults();
    }

    /**
     * The default {@link BusinessEconomyConfig} as JSON - the tuner builds its
     * Business section from this, the same way it builds the residential Economy
     * section from {@code /economy/defaults}. Applied only on {@code /generate}.
     */
    @GetMapping("/business/defaults")
    public BusinessEconomyConfig businessDefaults() {
        return BusinessEconomyConfig.defaults();
    }

    /**
     * Generates + renders a city in memory from the given knobs and returns the
     * PNG - nothing is stored. Any knob omitted keeps its default (see
     * {@link GenerationParams}). An impossible knob combination comes back as a
     * 400 with the validation message, so the tuner can surface why.
     * {@code cellSize} is px per tile (1-10); {@code seed} defaults to 42 so
     * sweeping a knob shows that knob's effect, not a new random map.
     */
    @GetMapping("/preview/render")
    public ResponseEntity<byte[]> previewRender(@ModelAttribute GenerationParams params,
                                                @RequestParam(required = false) Long seed,
                                                @RequestParam(defaultValue = "3") int cellSize,
                                                @RequestParam(defaultValue = "false") boolean showFloors) {
        long actualSeed = seed != null ? seed : 42L;
        try {
            byte[] png = previewService.renderPreview(params.toConfig(), actualSeed, cellSize, showFloors);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("invalid generation config: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
