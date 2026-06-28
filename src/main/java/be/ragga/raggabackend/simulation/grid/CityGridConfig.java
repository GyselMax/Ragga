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
