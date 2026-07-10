package be.ragga.raggabackend.simulation.grid.persistence.web;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws each building's floor count centered on its footprint, in white with a
 * black outline for contrast - a debug/visual aid for reading the skyline.
 * Shared by both renderers (stored-city {@link CityPngRenderer} and the
 * in-memory preview) so the overlay looks identical regardless of source; each
 * caller just adapts its own building type to a {@link Box}.
 */
public final class FloorOverlay {

    /** A building's footprint in tile coords plus its floor count. */
    public record Box(int cellX, int cellY, int cellsWide, int cellsDeep, int floors) {
    }

    private FloorOverlay() {
    }

    public static void draw(BufferedImage image, int cellSize, List<Box> buildings) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try {
            for (Box box : buildings) {
                if (box.floors() <= 0) {
                    continue;
                }
                int px = box.cellX() * cellSize;
                int py = box.cellY() * cellSize;
                int w = box.cellsWide() * cellSize;
                int h = box.cellsDeep() * cellSize;

                // Fit the glyph to the smaller footprint dimension; skip when it
                // would be too small to read.
                int fontSize = (int) Math.round(Math.min(w, h) * 0.75);
                if (fontSize < 7) {
                    continue;
                }
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
                FontMetrics fm = g.getFontMetrics();
                String text = Integer.toString(box.floors());
                int tx = px + (w - fm.stringWidth(text)) / 2;
                int ty = py + (h - fm.getHeight()) / 2 + fm.getAscent();

                g.setColor(Color.BLACK);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0) {
                            g.drawString(text, tx + dx, ty + dy);
                        }
                    }
                }
                g.setColor(Color.WHITE);
                g.drawString(text, tx, ty);
            }
        } finally {
            g.dispose();
        }
    }
}
