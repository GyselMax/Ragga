package be.ragga.raggabackend.simulation.grid.persistence;

import be.ragga.raggabackend.simulation.grid.GridVisualizer;
import be.ragga.raggabackend.simulation.grid.generation.BuildingDraft;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationPipeline;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.TemplateSpec;
import be.ragga.raggabackend.simulation.grid.persistence.catalog.TemplateCatalog;
import be.ragga.raggabackend.simulation.grid.persistence.web.FloorOverlay;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a city in memory and renders it to a PNG WITHOUT persisting - the
 * fast path behind the generation tuner, where the whole point is to tweak a
 * knob and immediately see the new map. Nothing touches the DB except reading
 * the (tiny) template catalog, so it costs nothing to spam while tuning.
 *
 * Rendering reuses {@link GridVisualizer}'s in-memory renderer (the canonical
 * GenerationResult painter) rather than duplicating its palette a third time.
 */
@Service
public class CityPreviewService {

    private final GenerationPipeline pipeline;
    private final TemplateCatalog templateCatalog;

    public CityPreviewService(GenerationPipeline pipeline, TemplateCatalog templateCatalog) {
        this.pipeline = pipeline;
        this.templateCatalog = templateCatalog;
    }

    public byte[] renderPreview(GenerationConfig config, long seed, int cellSize, boolean showFloors) {
        List<TemplateSpec> catalog = templateCatalog.templates();
        GenerationResult result = pipeline.generate(config, catalog, seed);

        // Clamp before handing off: GridVisualizer.renderToImage feeds the value
        // into Math.clamp(x, 1, requestedCellSize), which throws when
        // requestedCellSize < 1 (a min > max). Clamping here keeps a stray
        // ?cellSize=0 a harmless 1px render instead of a 500.
        int safeCellSize = Math.clamp(cellSize, 1, 10);

        BufferedImage image;
        // GridVisualizer.renderToImage writes a static cellSize field, so two
        // concurrent preview requests could corrupt each other's render.
        // Serialize the calls - preview traffic is one tinkering user, so the
        // lock is never contended in practice.
        synchronized (GridVisualizer.class) {
            image = GridVisualizer.renderToImage(result, safeCellSize);
        }

        if (showFloors) {
            // GridVisualizer may shrink the cell size for very large maps, so
            // derive the actual one from the rendered image rather than trusting
            // safeCellSize - otherwise the labels would drift off the buildings.
            int actualCellSize = image.getWidth() / config.width();
            List<FloorOverlay.Box> boxes = new ArrayList<>(result.buildings().size());
            for (BuildingDraft b : result.buildings()) {
                boxes.add(new FloorOverlay.Box(b.x(), b.y(), b.sizeX(), b.sizeY(), b.template().floors()));
            }
            FloorOverlay.draw(image, actualCellSize, boxes);
        }
        return toPng(image);
    }

    private byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PNG encoding failed", e);
        }
    }
}
