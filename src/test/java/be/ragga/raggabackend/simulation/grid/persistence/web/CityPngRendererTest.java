package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.generation.*;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.BuildingTemplate;
import be.ragga.raggabackend.simulation.grid.persistence.mapping.GenerationResultMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders a small generated city through the full mapper -> renderer path (no
 * Spring, no DB - the renderer only needs the entity graph) and verifies the
 * PNG decodes with the right dimensions and actually shows a city rather than
 * a blank sheet.
 */
class CityPngRendererTest {

    private static final long SEED = 42L;
    private static final int SIZE = 120;
    private static final int CELL_SIZE = 4;

    @Test
    void rendersDecodablePngWithCityContent() throws IOException {
        City city = generateCity();

        byte[] png = new CityPngRenderer().render(city, CELL_SIZE, false);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertEquals(SIZE * CELL_SIZE, image.getWidth(), "width must be tiles x cellSize");
        assertEquals(SIZE * CELL_SIZE, image.getHeight(), "height must be tiles x cellSize");

        // All three layers must actually have painted, not just the ground:
        // at least one light lot fill (layer 2) and one strong building fill
        // (layer 3) must appear among the sampled cell pixels. Ground colors
        // alone would already give 5+ distinct values, so a plain
        // distinct-color count can't catch a missing layer.
        Set<Integer> colors = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x += CELL_SIZE) {
            for (int y = 0; y < image.getHeight(); y += CELL_SIZE) {
                colors.add(image.getRGB(x, y) & 0xFFFFFF);
            }
        }
        // Light zone fills (lot layer) - any one proves the layer painted.
        Set<Integer> lotFills = Set.of(0xC8E6C9, 0xBBDEFB, 0xFFE0B2, 0xE8DFA0, 0xD9C49B);
        // Strong zone fills + public purple (building layer).
        Set<Integer> buildingFills = Set.of(0x2E7D32, 0x1565C0, 0xE65100, 0x795548, 0x6A1B9A);
        assertTrue(colors.stream().anyMatch(lotFills::contains),
                "no lot-layer color found - the lot layer did not paint");
        assertTrue(colors.stream().anyMatch(buildingFills::contains),
                "no building-layer color found - the building layer did not paint");
    }

    @Test
    void oversizedCellSizeIsClampedToMax() throws IOException {
        City city = generateCity();

        byte[] png = new CityPngRenderer().render(city, 999, false);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        // Clamped to MAX_CELL_SIZE (10), not an error and not unclamped.
        assertEquals(SIZE * 10, image.getWidth(), "cellSize must be clamped to 10 px per tile");
    }

    @Test
    void floorOverlayRendersWithoutError() throws IOException {
        City city = generateCity();

        // The overlay adds white glyphs; the image must still decode at the
        // right size. (Legibility is a visual concern, not asserted here.)
        byte[] png = new CityPngRenderer().render(city, 8, true);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(SIZE * 8, image.getWidth(), "floor overlay must not change dimensions");
    }

    private static City generateCity() {
        GenerationPipeline pipeline = new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService());
        GenerationConfig config = GenerationConfig.defaults(SIZE, SIZE);
        GenerationResult result = pipeline.generate(config, StubTemplateCatalog.standard(), SEED);

        Map<String, BuildingTemplate> templatesByCode = StubTemplateCatalog.standard().stream()
                .map(BuildingTemplate::from)
                .collect(Collectors.toMap(BuildingTemplate::getCode, Function.identity()));
        return new GenerationResultMapper().toCity(result, SEED, config, templatesByCode);
    }
}
