package be.ragga.raggabackend.simulation.generation.WFC;

// CityVisualizer.java
import javax.swing.*;
import java.awt.*;

public class CityVisualizer extends JPanel {
    private final WaveFunctionCollapse wfc;
    private final int cellSize = 20;

    public CityVisualizer(WaveFunctionCollapse wfc) {
        this.wfc = wfc;
        setPreferredSize(new Dimension(
                wfc.getWidth() * cellSize,
                wfc.getHeight() * cellSize
        ));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int x = 0; x < wfc.getWidth(); x++) {
            for (int y = 0; y < wfc.getHeight(); y++) {
                Tile tile = wfc.getTileAt(x, y);
                if (tile != null) {
                    g.setColor(getTileColor(tile.getId()));
                    g.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);

                    g.setColor(Color.BLACK);
                    g.drawRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
            }
        }
    }

    private Color getTileColor(String tileId) {
        switch (tileId) {
            case "empty": return new Color(34, 139, 34);      // Green
            case "building": return new Color(128, 128, 128); // Gray
            case "road_ns":
            case "road_ew":
            case "road_cross": return new Color(0, 0, 0);  // Dark gray
            default: return Color.WHITE;
        }
    }

    public static void display(WaveFunctionCollapse wfc) {
        JFrame frame = new JFrame("City Generation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new CityVisualizer(wfc));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}