package be.ragga.raggabackend.simulation.grid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CityGridConfig {

    @Value("${ragga.grid.width}")
    private int width;

    @Value("${ragga.grid.height}")
    private int height;

    @Value("${ragga.grid.zoneAnchorCount}")
    private int zoneAnchorCount;

    // No-arg constructor Spring uses to instantiate this bean before
    // injecting the @Value fields above.
    public CityGridConfig() {
    }

    // Package-private constructor for building this config outside of
    // Spring, e.g. from GridVisualizer's standalone main() method.
    CityGridConfig(int width, int height, int zoneAnchorCount) {
        this.width = width;
        this.height = height;
        this.zoneAnchorCount = zoneAnchorCount;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getZoneAnchorCount() {
        return zoneAnchorCount;
    }
}
