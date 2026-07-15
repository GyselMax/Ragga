package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.economy.BusinessEconomyConfig;
import be.ragga.raggabackend.simulation.economy.BusinessMarketService;
import be.ragga.raggabackend.simulation.economy.EconomyConfig;
import be.ragga.raggabackend.simulation.economy.PopulationService;
import be.ragga.raggabackend.simulation.grid.CityRepository;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationPipeline;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplateRepository;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.TemplateCatalog;
import be.ragga.raggabackend.simulation.grid.persistence.mapping.GenerationResultMapper;
import be.ragga.raggabackend.simulation.grid.persistence.web.CityPngRenderer;
import be.ragga.raggabackend.simulation.grid.persistence.web.CitySummary;
import be.ragga.raggabackend.simulation.grid.persistence.web.EconomyHeatmapRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Orchestrates the whole store-a-city flow: run the generation pipeline against
 * the DB-seeded catalog, map the result onto the {@link City} entity graph, and
 * persist it. The generation package stays untouched and JPA-free - this
 * service is the seam that turns its output into rows.
 */
@Service
public class CityPersistenceService {

    private final GenerationPipeline pipeline;
    private final TemplateCatalog templateCatalog;
    private final BuildingTemplateRepository templateRepository;
    private final GenerationResultMapper mapper;
    private final CityRepository cityRepository;
    private final CityPngRenderer pngRenderer;
    private final EconomyHeatmapRenderer heatmapRenderer;
    private final PopulationService populationService;
    private final BusinessMarketService businessMarketService;

    public CityPersistenceService(GenerationPipeline pipeline,
                                  TemplateCatalog templateCatalog,
                                  BuildingTemplateRepository templateRepository,
                                  GenerationResultMapper mapper,
                                  CityRepository cityRepository,
                                  CityPngRenderer pngRenderer,
                                  EconomyHeatmapRenderer heatmapRenderer,
                                  PopulationService populationService,
                                  BusinessMarketService businessMarketService) {
        this.pipeline = pipeline;
        this.templateCatalog = templateCatalog;
        this.templateRepository = templateRepository;
        this.mapper = mapper;
        this.cityRepository = cityRepository;
        this.pngRenderer = pngRenderer;
        this.heatmapRenderer = heatmapRenderer;
        this.populationService = populationService;
        this.businessMarketService = businessMarketService;
    }

    /**
     * Generates a city from the given config + seed and persists it in one
     * transaction. The config is the fully-resolved set of knobs (the tuner
     * builds it from the sliders); the seed makes it reproducible.
     *
     * @return the persisted city
     */
    @Transactional
    public City generateAndSave(GenerationConfig config, long seed) {
        return generateAndSave(config, null, null, seed);
    }

    /**
     * As {@link #generateAndSave(GenerationConfig, long)}, but drives the residential
     * economy pass with a request-supplied {@link EconomyConfig}. A null
     * {@code economy} keeps the calibrated defaults; the business market keeps its
     * default config.
     */
    @Transactional
    public City generateAndSave(GenerationConfig config, EconomyConfig economy, long seed) {
        return generateAndSave(config, economy, null, seed);
    }

    /**
     * As above, but also drives the non-residential (business) market pass with a
     * request-supplied {@link BusinessEconomyConfig} - the persisting twin of the
     * tuner's economy sliders. Null overrides keep the respective calibrated
     * defaults.
     *
     * @return the persisted city
     */
    @Transactional
    public City generateAndSave(GenerationConfig config, EconomyConfig economy,
                                BusinessEconomyConfig business, long seed) {
        List<TemplateSpec> catalog = templateCatalog.templates();
        GenerationResult result = pipeline.generate(config, catalog, seed);

        Map<String, BuildingTemplate> templatesByCode = templateRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(BuildingTemplate::getCode, Function.identity()));

        City city = mapper.toCity(result, seed, config, templatesByCode);
        // Give the freshly-mapped city a cleared housing market and a placed
        // population, then price and own the non-residential stock with companies -
        // all before persistence, so a saved city is born economically alive
        // (values, rents, tenures, households, companies) and every owner/premises
        // cross-link resolves within this one transaction.
        populationService.populate(city, result, seed, economy);
        businessMarketService.clear(city, result, seed, business);
        return cityRepository.save(city);
    }

    /**
     * Loads a stored city as a lightweight summary. Runs in a transaction so the
     * lazy child collections can be counted without a full fetch of the ~250k
     * grid cells.
     */
    @Transactional(readOnly = true)
    public Optional<CitySummary> getSummary(long id) {
        return cityRepository.findById(id).map(CitySummary::of);
    }

    /** Every stored city as a summary - lets a caller discover what's in the DB. */
    @Transactional(readOnly = true)
    public List<CitySummary> listSummaries() {
        return cityRepository.findAll().stream().map(CitySummary::of).toList();
    }

    /**
     * Renders a stored city to a PNG (see {@link CityPngRenderer}). Runs in a
     * transaction because rendering walks every lazy child collection,
     * including the full ~width x height grid raster.
     *
     * @param showFloors overlay each building's floor count as a visual aid
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> renderPng(long id, int cellSize, boolean showFloors) {
        return cityRepository.findById(id).map(city -> pngRenderer.render(city, cellSize, showFloors));
    }

    /**
     * Renders a stored city's residential economy to a PNG heatmap (see
     * {@link EconomyHeatmapRenderer}). Transactional because rendering walks the
     * lazy grid, buildings and households. Meaningful only for a city generated
     * after the economy pass existed; older cities render as unpriced/vacant.
     *
     * @param mode value heatmap or tenure map
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> renderEconomyHeatmap(long id, int cellSize, EconomyHeatmapRenderer.Mode mode) {
        return cityRepository.findById(id).map(city -> heatmapRenderer.render(city, cellSize, mode));
    }
}
