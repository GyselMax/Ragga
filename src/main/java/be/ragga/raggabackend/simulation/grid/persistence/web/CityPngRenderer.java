package be.ragga.raggabackend.simulation.grid.persistence.web;

import be.ragga.raggabackend.simulation.City;
import be.ragga.raggabackend.simulation.grid.GridCell;
import be.ragga.raggabackend.simulation.grid.TileType;
import be.ragga.raggabackend.simulation.grid.ZoneType;
import be.ragga.raggabackend.simulation.grid.lot.Direction;
import be.ragga.raggabackend.simulation.grid.persistence.entity.Lot;
import be.ragga.raggabackend.simulation.grid.persistence.entity.PlacedBuilding;
import be.ragga.raggabackend.simulation.grid.persistence.entity.RoadSegment;
import be.ragga.raggabackend.simulation.grid.road.RoadClass;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Renders a STORED city (the persisted entity graph) to a PNG - the API
 * counterpart of the standalone {@link be.ragga.raggabackend.simulation.grid.GridVisualizer},
 * which renders an in-memory GenerationResult instead. Same three layers and
 * the same color legend (see GENERATION.md): ground per tile, zone-tinted
 * lots with tan vacants, strong building blocks with a white front notch.
 *
 * The palette below deliberately mirrors GridVisualizer's constants - if you
 * retune colors there, retune them here too so both views stay comparable.
 *
 * Road classes are not stored per tile; they are repainted from the city's
 * RoadSegments, more major class winning where segments cross - the same rule
 * the generator used to build the raster in the first place.
 *
 * Stateless and thread-safe: all rendering state lives in locals.
 */
@Component
public class CityPngRenderer {

    // Ground colors per physical tile type (roads shaded by class).
    private static final int COLOR_ROAD_ARTERIAL = 0x22262A;
    private static final int COLOR_ROAD_COLLECTOR = 0x3E444B;
    private static final int COLOR_ROAD_LOCAL = 0x545C64;
    private static final int COLOR_PARK = 0x4CAF50;
    private static final int COLOR_PUBLIC = 0xB39CD8;
    private static final int COLOR_UNUSED = 0xD3D3D3;
    private static final int COLOR_WATER = 0x3D7EBF;
    private static final int COLOR_FOREST = 0x2E6B34;

    // Lot ground fill per zone (light shades - buildings get the strong shade).
    private static final Map<ZoneType, Integer> LOT_FILL = Map.of(
            ZoneType.RESIDENTIAL, 0xC8E6C9,
            ZoneType.COMMERCIAL, 0xBBDEFB,
            ZoneType.INDUSTRIAL, 0xFFE0B2,
            ZoneType.UNDER_CONSTRUCTION, 0xFFF59D,
            ZoneType.FARMLAND, 0xE8DFA0
    );
    private static final int COLOR_VACANT_LOT = 0xD9C49B;

    // Building fill per zone (strong shades) + public purple.
    private static final Map<ZoneType, Integer> BUILDING_FILL = Map.of(
            ZoneType.RESIDENTIAL, 0x2E7D32,
            ZoneType.COMMERCIAL, 0x1565C0,
            ZoneType.INDUSTRIAL, 0xE65100,
            ZoneType.UNDER_CONSTRUCTION, 0xF9A825,
            ZoneType.FARMLAND, 0x795548
    );
    private static final int COLOR_BUILDING_PUBLIC = 0x6A1B9A;

    private static final int COLOR_LOT_BORDER = 0x78909C;
    private static final int COLOR_BUILDING_BORDER = 0x000000;
    private static final int COLOR_FACING_MARK = 0xFFFFFF;

    // Requested px-per-tile is clamped to this range, and further shrunk if
    // the image would exceed MAX_IMAGE_EDGE px per side (a 5000-tile map at
    // 10 px would blow past Java's max array length). The shrink floors at
    // 1 px per tile, so maps beyond MAX_IMAGE_EDGE tiles per side would still
    // exceed the cap - fine for now, current maps are 500x500.
    private static final int MIN_CELL_SIZE = 1;
    private static final int MAX_CELL_SIZE = 10;
    private static final int MAX_IMAGE_EDGE = 20_000;

    public byte[] render(City city, int requestedCellSize, boolean showFloors) {
        int width = city.getGenerationConfig().width();
        int height = city.getGenerationConfig().height();
        int cellSize = Math.clamp(requestedCellSize, MIN_CELL_SIZE, MAX_CELL_SIZE);
        cellSize = Math.clamp(MAX_IMAGE_EDGE / Math.max(width, height), MIN_CELL_SIZE, cellSize);

        TileType[][] tiles = rebuildTiles(city, width, height);
        RoadClass[][] roadClasses = rebuildRoadClasses(city, width, height);

        BufferedImage image = new BufferedImage(width * cellSize, height * cellSize, BufferedImage.TYPE_INT_RGB);

        // 1. Ground layer: every cell colored by its physical tile type.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                fillRect(image, cellSize, x, y, 1, 1, groundColor(tiles[x][y], roadClasses[x][y]));
            }
        }

        // 2. Lot layer: zone-colored fill + parcel border, vacant lots in tan.
        for (Lot lot : city.getLots()) {
            if (lot.isPublicSite()) {
                continue; // ground already purple via TileType.PUBLIC
            }
            int fill = lot.isVacant() ? COLOR_VACANT_LOT : LOT_FILL.get(lot.getZone());
            fillRect(image, cellSize, lot.getOrigin().getX(), lot.getOrigin().getY(),
                    lot.getWidth(), lot.getDepth(), fill);
            drawRectBorder(image,
                    lot.getOrigin().getX() * cellSize, lot.getOrigin().getY() * cellSize,
                    lot.getWidth() * cellSize, lot.getDepth() * cellSize,
                    COLOR_LOT_BORDER);
        }

        // 3. Building layer: strong fill + black outline + white front notch.
        for (PlacedBuilding building : city.getBuildings()) {
            int fill = building.getTemplate().isPublicUse()
                    ? COLOR_BUILDING_PUBLIC
                    : BUILDING_FILL.get(building.getTemplate().getZone());
            fillRect(image, cellSize, building.getOrigin().getX(), building.getOrigin().getY(),
                    building.getSizeX(), building.getSizeY(), fill);
            drawRectBorder(image,
                    building.getOrigin().getX() * cellSize, building.getOrigin().getY() * cellSize,
                    building.getSizeX() * cellSize, building.getSizeY() * cellSize,
                    COLOR_BUILDING_BORDER);
            drawFacingMark(image, cellSize, building);
        }

        // 4. Optional overlay: the blueprint's floor count centered on each
        // building - a debug/visual aid for reading the skyline at a glance.
        if (showFloors) {
            drawFloorOverlay(image, cellSize, city);
        }

        return toPngBytes(image);
    }

    /** Overlays each building's floor count (from its template); see {@link FloorOverlay}. */
    private void drawFloorOverlay(BufferedImage image, int cellSize, City city) {
        List<FloorOverlay.Box> boxes = new ArrayList<>();
        for (PlacedBuilding building : city.getBuildings()) {
            boxes.add(new FloorOverlay.Box(
                    building.getOrigin().getX(), building.getOrigin().getY(),
                    building.getSizeX(), building.getSizeY(),
                    building.getTemplate().getFloors()));
        }
        FloorOverlay.draw(image, cellSize, boxes);
    }

    private TileType[][] rebuildTiles(City city, int width, int height) {
        TileType[][] tiles = new TileType[width][height];
        for (GridCell cell : city.getGrid()) {
            tiles[cell.getPosition().getX()][cell.getPosition().getY()] = cell.getTileType();
        }
        return tiles;
    }

    private RoadClass[][] rebuildRoadClasses(City city, int width, int height) {
        RoadClass[][] classes = new RoadClass[width][height];
        for (RoadSegment segment : city.getRoads()) {
            RoadClass roadClass = segment.getRoadClass();
            int x0 = Math.min(segment.getStart().getX(), segment.getEnd().getX());
            int x1 = Math.max(segment.getStart().getX(), segment.getEnd().getX());
            int y0 = Math.min(segment.getStart().getY(), segment.getEnd().getY());
            int y1 = Math.max(segment.getStart().getY(), segment.getEnd().getY());
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    // Where segments cross, the more major class (lower
                    // ordinal) wins - same rule the generator applies.
                    if (classes[x][y] == null || roadClass.ordinal() < classes[x][y].ordinal()) {
                        classes[x][y] = roadClass;
                    }
                }
            }
        }
        return classes;
    }

    private int groundColor(TileType tile, RoadClass roadClass) {
        if (tile == null) {
            return COLOR_UNUSED; // defensive: a gap in the raster shows gray, not a crash
        }
        return switch (tile) {
            case ROAD -> switch (roadClass == null ? RoadClass.LOCAL : roadClass) {
                case ARTERIAL -> COLOR_ROAD_ARTERIAL;
                case COLLECTOR -> COLOR_ROAD_COLLECTOR;
                case LOCAL -> COLOR_ROAD_LOCAL;
            };
            case PARK -> COLOR_PARK;
            case PUBLIC -> COLOR_PUBLIC;
            case LOT, UNUSED -> COLOR_UNUSED; // LOT is overpainted by the lot layer
            case WATER -> COLOR_WATER;
            case FOREST -> COLOR_FOREST;
        };
    }

    private void fillRect(BufferedImage image, int cellSize, int cellX, int cellY,
                          int cellsWide, int cellsDeep, int color) {
        // Row-wise fills straight into the backing int array - setRGB per
        // pixel costs seconds on large maps (same trick as GridVisualizer).
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int stride = image.getWidth();
        int px = cellX * cellSize;
        int py = cellY * cellSize;
        int w = cellsWide * cellSize;
        int h = cellsDeep * cellSize;
        for (int dy = 0; dy < h; dy++) {
            int rowStart = (py + dy) * stride + px;
            Arrays.fill(data, rowStart, rowStart + w, color);
        }
    }

    private void drawRectBorder(BufferedImage image, int px, int py, int w, int h, int color) {
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
    private void drawFacingMark(BufferedImage image, int cellSize, PlacedBuilding building) {
        int px = building.getOrigin().getX() * cellSize;
        int py = building.getOrigin().getY() * cellSize;
        int w = building.getSizeX() * cellSize;
        int h = building.getSizeY() * cellSize;
        // Clamped to the footprint: at small cellSize the fixed minimum would
        // otherwise draw outside 1x1 buildings (and off the image at the border).
        int markLength = Math.min(Math.max(cellSize / 2, 4), Math.min(w, h) - 2);
        if (markLength < 1) {
            return;
        }

        Direction facing = building.getFacing();
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

    private byte[] toPngBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream cannot actually fail on IO; keep the
            // signature checked-exception-free for callers.
            throw new UncheckedIOException("PNG encoding failed", e);
        }
    }
}
