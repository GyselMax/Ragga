package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.grid.generation.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the in-memory preview render path (pipeline -> GridVisualizer ->
 * PNG) without Spring or a DB: the catalog is supplied as a lambda returning
 * the stub library.
 */
class CityPreviewServiceTest {

    private static final int SIZE = 120;

    private CityPreviewService service() {
        GenerationPipeline pipeline = new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService());
        return new CityPreviewService(pipeline, StubTemplateCatalog::standard);
    }

    @Test
    void rendersDecodablePngAtRequestedCellSize() throws IOException {
        byte[] png = service().renderPreview(GenerationConfig.defaults(SIZE, SIZE), 42L, 3, false);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(SIZE * 3, image.getWidth(), "width must be tiles x cellSize");
        assertEquals(SIZE * 3, image.getHeight(), "height must be tiles x cellSize");
    }

    @Test
    void zeroCellSizeIsClampedNotCrashing() throws IOException {
        // GridVisualizer.renderToImage would throw on cellSize < 1 (Math.clamp
        // min > max); the service must clamp to 1 first.
        byte[] png = service().renderPreview(GenerationConfig.defaults(SIZE, SIZE), 42L, 0, false);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(SIZE, image.getWidth(), "cellSize 0 must clamp to 1 px per tile");
    }

    @Test
    void isDeterministicPerSeed() {
        CityPreviewService service = service();
        GenerationConfig config = GenerationConfig.defaults(SIZE, SIZE);
        byte[] first = service.renderPreview(config, 7L, 2, false);
        byte[] second = service.renderPreview(config, 7L, 2, false);
        assertTrue(java.util.Arrays.equals(first, second),
                "same seed + config must render byte-identical PNGs");
    }

    @Test
    void floorOverlayRendersWithoutError() throws IOException {
        // Larger cell size so glyphs are drawn; must still decode at the right size.
        byte[] png = service().renderPreview(GenerationConfig.defaults(SIZE, SIZE), 42L, 8, true);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(SIZE * 8, image.getWidth(), "floor overlay must not change dimensions");
    }
}
