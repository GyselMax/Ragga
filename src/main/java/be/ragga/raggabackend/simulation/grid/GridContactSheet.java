package be.ragga.raggabackend.simulation.grid;

import be.ragga.raggabackend.simulation.grid.generation.BlockSubdivisionService;
import be.ragga.raggabackend.simulation.grid.generation.BuildingPlacementService;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationPipeline;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.LotSubdivisionService;
import be.ragga.raggabackend.simulation.grid.generation.RoadNetworkGenerator;
import be.ragga.raggabackend.simulation.grid.generation.SettlementPlanner;
import be.ragga.raggabackend.simulation.grid.generation.StubTemplateCatalog;
import be.ragga.raggabackend.simulation.grid.generation.TerrainGenerator;
import be.ragga.raggabackend.simulation.grid.generation.ZoneAssignmentService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Debug-only tuning tool: renders a 3x3 sheet of DIFFERENT seeds into one
 * contact-sheet.png, so parameter changes are judged against the seed
 * VARIANCE instead of a single (possibly lucky) seed. Each thumbnail is
 * labeled with its seed - spot an interesting city here, then reproduce it
 * full-size by passing that seed to GridVisualizer.
 *
 * Run from IntelliJ like GridVisualizer. Optional first argument = base
 * seed (the sheet uses baseSeed..baseSeed+8); without it a random base is
 * used and printed.
 */
public final class GridContactSheet {

    private static final int COLS = 3;
    private static final int ROWS = 3;
    private static final int MAP_SIZE = 1000;
    private static final int CELL_SIZE = 2;
    private static final int LABEL_HEIGHT = 24;
    private static final int PADDING = 8;

    private GridContactSheet() {
    }

    public static void main(String[] args) throws IOException {
        long baseSeed = args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong();

        GenerationPipeline pipeline = new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService()
        );
        GenerationConfig config = GenerationConfig.defaults(MAP_SIZE, MAP_SIZE);

        int tile = MAP_SIZE * CELL_SIZE;
        int sheetWidth = COLS * tile + (COLS + 1) * PADDING;
        int sheetHeight = ROWS * (tile + LABEL_HEIGHT) + (ROWS + 1) * PADDING;
        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sheetWidth, sheetHeight);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));

        for (int i = 0; i < COLS * ROWS; i++) {
            long seed = baseSeed + i;
            GenerationResult result = pipeline.generate(config, StubTemplateCatalog.standard(), seed);
            BufferedImage thumb = GridVisualizer.renderToImage(result, CELL_SIZE);

            int x = PADDING + (i % COLS) * (tile + PADDING);
            int y = PADDING + (i / COLS) * (tile + LABEL_HEIGHT + PADDING);
            g.drawImage(thumb, x, y, null);
            g.setColor(Color.BLACK);
            g.drawString("seed " + seed, x, y + tile + LABEL_HEIGHT - 8);
            System.out.println("rendered seed " + seed);
        }
        g.dispose();

        ImageIO.write(sheet, "png", new File("contact-sheet.png"));
        System.out.println("base seed: " + baseSeed + " -> contact-sheet.png");
    }
}
