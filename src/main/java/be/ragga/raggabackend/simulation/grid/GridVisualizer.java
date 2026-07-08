package be.ragga.raggabackend.simulation.grid;

import be.ragga.raggabackend.simulation.grid.generation.BlockSubdivisionService;
import be.ragga.raggabackend.simulation.grid.generation.BuildingDraft;
import be.ragga.raggabackend.simulation.grid.generation.BuildingPlacementService;
import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import be.ragga.raggabackend.simulation.grid.generation.GenerationPipeline;
import be.ragga.raggabackend.simulation.grid.generation.GenerationResult;
import be.ragga.raggabackend.simulation.grid.generation.GenerationSanityCheck;
import be.ragga.raggabackend.simulation.grid.generation.LotDraft;
import be.ragga.raggabackend.simulation.grid.generation.LotSubdivisionService;
import be.ragga.raggabackend.simulation.grid.generation.RoadNetworkGenerator;
import be.ragga.raggabackend.simulation.grid.generation.SettlementPlanner;
import be.ragga.raggabackend.simulation.grid.generation.StubTemplateCatalog;
import be.ragga.raggabackend.simulation.grid.generation.TerrainGenerator;
import be.ragga.raggabackend.simulation.grid.generation.ZoneAssignmentService;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

/**
 * Debug-only utility for visually judging generation quality.
 * Runs the full generation pipeline standalone - no Spring context, no
 * database (uses the StubTemplateCatalog, not the real DB-seeded library) -
 * and renders the result to grid.png.
 *
 * Run directly from IntelliJ (green arrow next to main). Pass a seed as the
 * first program argument to reproduce a previous render; without arguments a
 * random seed is used and printed, so any interesting result can be
 * revisited.
 */
public final class GridVisualizer {

    // *** CHANGE THIS to scale the output image: each grid cell is drawn as a
    // CELL_SIZE x CELL_SIZE pixel block, so a 1000x1000 map at CELL_SIZE 10
    // is a 10000x10000 px PNG. Bigger = more detail, larger file. ***
    private static final int CELL_SIZE = 5;

    // Safety only: the effective cell size is shrunk below CELL_SIZE just
    // enough to keep the image under MAX_IMAGE_EDGE px per side. TYPE_INT_RGB
    // backs the image with ONE int array, so an oversized edge (e.g. 5000
    // tiles x 10 px = 50000 px) blows past Java's max array length (~2.1B
    // ints) and can't be allocated. This cap only bites on very large maps;
    // for normal maps CELL_SIZE is used as-is.
    private static final int MAX_IMAGE_EDGE = 100_000;
    private static int cellSize = CELL_SIZE;

    // Ground colors per physical tile type (roads shaded by class).
    private static final int COLOR_ROAD_ARTERIAL = 0x22262A;
    private static final int COLOR_ROAD_COLLECTOR = 0x3E444B;
    private static final int COLOR_ROAD_LOCAL = 0x545C64;
    private static final int COLOR_PARK = 0x4CAF50;      // green
    private static final int COLOR_PUBLIC = 0xB39CD8;    // light purple ground
    private static final int COLOR_UNUSED = 0xD3D3D3;    // light gray
    private static final int COLOR_WATER = 0x3D7EBF;     // river blue
    private static final int COLOR_FOREST = 0x2E6B34;    // dark woodland green

    // Lot ground fill per zone (light shades - buildings get the strong shade).
    private static final Map<ZoneType, Integer> LOT_FILL = Map.of(
            ZoneType.RESIDENTIAL, 0xC8E6C9,      // light green
            ZoneType.COMMERCIAL, 0xBBDEFB,       // light blue
            ZoneType.INDUSTRIAL, 0xFFE0B2,       // light orange
            ZoneType.UNDER_CONSTRUCTION, 0xFFF59D, // yellow (never emitted by the generator)
            ZoneType.FARMLAND, 0xE8DFA0          // wheat
    );
    private static final int COLOR_VACANT_LOT = 0xD9C49B; // tan

    // Building fill per zone (strong shades) + public purple.
    private static final Map<ZoneType, Integer> BUILDING_FILL = Map.of(
            ZoneType.RESIDENTIAL, 0x2E7D32,
            ZoneType.COMMERCIAL, 0x1565C0,
            ZoneType.INDUSTRIAL, 0xE65100,
            ZoneType.UNDER_CONSTRUCTION, 0xF9A825,
            ZoneType.FARMLAND, 0x795548          // barn brown
    );
    private static final int COLOR_BUILDING_PUBLIC = 0x6A1B9A;

    private static final int COLOR_LOT_BORDER = 0x78909C;
    private static final int COLOR_BUILDING_BORDER = 0x000000;
    private static final int COLOR_FACING_MARK = 0xFFFFFF;

    private GridVisualizer() {
    }

    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong();

        // Pipeline components constructed by hand - they are stateless Spring
        // components, so 'new' works fine without a context.
        GenerationPipeline pipeline = new GenerationPipeline(
                new TerrainGenerator(),
                new SettlementPlanner(),
                new RoadNetworkGenerator(),
                new BlockSubdivisionService(),
                new LotSubdivisionService(),
                new ZoneAssignmentService(),
                new BuildingPlacementService()
        );

        //

        // CHANGE THE SIZE OF THE GENERATION HERE !!

        //
        GenerationConfig config = GenerationConfig.defaults(1000, 1000);
        GenerationResult result = pipeline.generate(config, StubTemplateCatalog.standard(), seed);

        render(result, "grid.png");
        GenerationSanityCheck.run(result);
        System.out.println("seed: " + seed + " -> grid.png");
    }

    public static void render(GenerationResult result, String outputPath) throws IOException {
        ImageIO.write(renderToImage(result), "png", new File(outputPath));
    }

    public static BufferedImage renderToImage(GenerationResult result) {
        return renderToImage(result, CELL_SIZE);
    }

    /**
     * Renders at the requested cell size (px per tile), shrinking it only when
     * the image would exceed MAX_IMAGE_EDGE per side. GridContactSheet passes a
     * small override for thumbnails.
     */
    public static BufferedImage renderToImage(GenerationResult result, int requestedCellSize) {
        int width = result.config().width();
        int height = result.config().height();
        cellSize = Math.clamp(MAX_IMAGE_EDGE / Math.max(width, height), 1, requestedCellSize);
        BufferedImage image = new BufferedImage(width * cellSize, height * cellSize, BufferedImage.TYPE_INT_RGB);

        // 1. Ground layer: every cell colored by its physical tile type.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                fillCell(image, x, y, groundColor(result, x, y));
            }
        }

        // 2. Lot layer: zone-colored fill + parcel border, vacant lots in tan
        // so purchasable land is visible at a glance.
        for (LotDraft lot : result.lots()) {
            if (lot.isPublicSite()) {
                continue; // ground already purple via TileType.PUBLIC
            }
            int fill = lot.isVacant() ? COLOR_VACANT_LOT : LOT_FILL.get(lot.getZone());
            fillRect(image, lot.getX(), lot.getY(), lot.getWidth(), lot.getDepth(), fill);
            drawRectBorder(image,
                    lot.getX() * cellSize, lot.getY() * cellSize,
                    lot.getWidth() * cellSize, lot.getDepth() * cellSize,
                    COLOR_LOT_BORDER);
        }

        // 3. Building layer: strong fill + black outline + a white mark on the
        // road-facing edge, so template size AND rotation are verifiable.
        for (BuildingDraft building : result.buildings()) {
            int fill = building.template().publicUse()
                    ? COLOR_BUILDING_PUBLIC
                    : BUILDING_FILL.get(building.template().zone());
            fillRect(image, building.x(), building.y(), building.sizeX(), building.sizeY(), fill);
            drawRectBorder(image,
                    building.x() * cellSize, building.y() * cellSize,
                    building.sizeX() * cellSize, building.sizeY() * cellSize,
                    COLOR_BUILDING_BORDER);
            drawFacingMark(image, building);
        }

        return image;
    }

    private static int groundColor(GenerationResult result, int x, int y) {
        TileType tile = result.tiles()[x][y];
        return switch (tile) {
            case ROAD -> switch (result.roadClasses()[x][y]) {
                case ARTERIAL -> COLOR_ROAD_ARTERIAL;
                case COLLECTOR -> COLOR_ROAD_COLLECTOR;
                case LOCAL -> COLOR_ROAD_LOCAL;
            };
            case PARK -> COLOR_PARK;
            case PUBLIC -> COLOR_PUBLIC;
            case LOT -> COLOR_UNUSED; // overpainted by the lot layer
            case UNUSED -> COLOR_UNUSED;
            case WATER -> COLOR_WATER;
            case FOREST -> COLOR_FOREST;
        };
    }

    private static void fillCell(BufferedImage image, int cellX, int cellY, int color) {
        fillPixels(image, cellX * cellSize, cellY * cellSize, cellSize, cellSize, color);
    }

    private static void fillRect(BufferedImage image, int cellX, int cellY, int cellsWide, int cellsDeep, int color) {
        fillPixels(image, cellX * cellSize, cellY * cellSize, cellsWide * cellSize, cellsDeep * cellSize, color);
    }

    private static void fillPixels(BufferedImage image, int px, int py, int w, int h, int color) {
        // Row-wise fills straight into the image's backing int array - the
        // per-pixel setRGB version cost seconds on large maps.
        int[] data = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int stride = image.getWidth();
        for (int dy = 0; dy < h; dy++) {
            int rowStart = (py + dy) * stride + px;
            java.util.Arrays.fill(data, rowStart, rowStart + w, color);
        }
    }

    private static void drawRectBorder(BufferedImage image, int px, int py, int w, int h, int color) {
        for (int dx = 0; dx < w; dx++) {
            image.setRGB(px + dx, py, color);
            image.setRGB(px + dx, py + h - 1, color);
        }
        for (int dy = 0; dy < h; dy++) {
            image.setRGB(px, py + dy, color);
            image.setRGB(px + w - 1, py + dy, color);
        }
    }

    /** Short white line centered on the building's road-facing edge - its "front door". */
    private static void drawFacingMark(BufferedImage image, BuildingDraft building) {
        int px = building.x() * cellSize;
        int py = building.y() * cellSize;
        int w = building.sizeX() * cellSize;
        int h = building.sizeY() * cellSize;
        // Clamped to the footprint: at small cellSize the fixed minimum
        // would otherwise draw outside 1x1 buildings (and off the image at
        // the map border).
        int markLength = Math.min(Math.max(cellSize / 2, 4), Math.min(w, h) - 2);
        if (markLength < 1) {
            return;
        }

        Direction facing = building.facing();
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            int y = facing == Direction.NORTH ? py + 1 : py + h - 2;
            int startX = px + w / 2 - markLength / 2;
            for (int i = 0; i < markLength; i++) {
                image.setRGB(startX + i, y, COLOR_FACING_MARK);
                image.setRGB(startX + i, y + (facing == Direction.NORTH ? 1 : -1), COLOR_FACING_MARK);
            }
        } else {
            int x = facing == Direction.WEST ? px + 1 : px + w - 2;
            int startY = py + h / 2 - markLength / 2;
            for (int i = 0; i < markLength; i++) {
                image.setRGB(x, startY + i, COLOR_FACING_MARK);
                image.setRGB(x + (facing == Direction.WEST ? 1 : -1), startY + i, COLOR_FACING_MARK);
            }
        }
    }
}
