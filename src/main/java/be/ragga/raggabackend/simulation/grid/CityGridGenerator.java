package be.ragga.raggabackend.simulation.grid;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CityGridGenerator {

    private final CityGridConfig config;
    private final Random random = new Random();

    public CityGridGenerator(CityGridConfig config) {
        this.config = config;
    }

    public List<GridCell> generate() {
        int width = config.getWidth();
        int height = config.getHeight();

        Map<ZoneType, GridPosition> anchors = generateAnchors(width, height);
        List<GridCell> cells = new ArrayList<>(width * height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridPosition pos = new GridPosition(x, y);
                ZoneType zone = findNearestZone(pos, anchors);
                cells.add(new GridCell(pos, zone));
            }
        }

        return cells;
    }

    private Map<ZoneType, GridPosition> generateAnchors(int width, int height) {
        Map<ZoneType, GridPosition> anchors = new EnumMap<>(ZoneType.class);
        for (ZoneType zone : ZoneType.values()) {
            if (zone == ZoneType.EMPTY) {
                continue;
            }
            anchors.put(zone, new GridPosition(random.nextInt(width), random.nextInt(height)));
        }
        return anchors;
    }

    private ZoneType findNearestZone(GridPosition pos, Map<ZoneType, GridPosition> anchors) {
        ZoneType nearest = ZoneType.EMPTY;
        double minDistance = Double.MAX_VALUE;
        boolean tie = false;

        for (Map.Entry<ZoneType, GridPosition> entry : anchors.entrySet()) {
            double distance = pos.distanceTo(entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = entry.getKey();
                tie = false;
            } else if (distance == minDistance) {
                tie = true;
            }
        }

        return tie ? ZoneType.EMPTY : nearest;
    }
}
