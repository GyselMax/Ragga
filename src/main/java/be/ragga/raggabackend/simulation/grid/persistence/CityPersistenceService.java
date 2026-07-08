package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.CityGridConfig;
import be.ragga.raggabackend.simulation.grid.CityRepository;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationPipeline;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplateRepository;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.TemplateCatalog;
import be.ragga.raggabackend.simulation.grid.persistence.mapping.GenerationResultMapper;
import be.ragga.raggabackend.simulation.grid.persistence.web.CitySummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
    private final CityGridConfig gridConfig;

    public CityPersistenceService(GenerationPipeline pipeline,
                                  TemplateCatalog templateCatalog,
                                  BuildingTemplateRepository templateRepository,
                                  GenerationResultMapper mapper,
                                  CityRepository cityRepository,
                                  CityGridConfig gridConfig) {
        this.pipeline = pipeline;
        this.templateCatalog = templateCatalog;
        this.templateRepository = templateRepository;
        this.mapper = mapper;
        this.cityRepository = cityRepository;
        this.gridConfig = gridConfig;
    }

    /**
     * Generates a city and persists it in one transaction.
     *
     * @param seed the seed to generate from, or null for a fresh random one
     *             (matching the visualizer's reproducibility convention)
     * @return the persisted city
     */
    @Transactional
    public City generateAndSave(Long seed) {
        long actualSeed = seed != null ? seed : new Random().nextLong();

        // Only width/height are exposed as properties today, so the remaining
        // knobs come from GenerationConfig.defaults - the same config the
        // standalone visualizer renders with.
        GenerationConfig config = GenerationConfig.defaults(gridConfig.getWidth(), gridConfig.getHeight());
        List<TemplateSpec> catalog = templateCatalog.templates();
        GenerationResult result = pipeline.generate(config, catalog, actualSeed);

        Map<String, BuildingTemplate> templatesByCode = templateRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(BuildingTemplate::getCode, Function.identity()));

        City city = mapper.toCity(result, actualSeed, config, templatesByCode);
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
}
