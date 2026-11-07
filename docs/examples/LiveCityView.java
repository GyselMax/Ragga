package be.magycks.ragga.generation.view;

import be.magycks.ragga.generation.Node;
import be.magycks.ragga.generation.quadtree.Leaf;
import be.magycks.ragga.generation.quadtree.QuadTree;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

// a window that draws the city while GraphGenerator builds it — the generator calls the hooks, they do nothing when the view is off
public final class LiveCityView {
    // does nothing at all — what open() returns when the view is switched off or Java has no screen
    public static final LiveCityView NONE = new LiveCityView(1, 1, 0, 0);

    private static final Color BACKGROUND = new Color(0x12171b);
    private static final Color SQUARE = new Color(0x2c3841);
    private static final Color STREET = new Color(0x9cabb5);
    private static final Color NODE = new Color(0xdde4e8);
    private static final Color LATEST = new Color(0xe0aa5c);
    private static final Color CURRENT = new Color(0x86bbe0);
    private static final int STATUS_BAR = 28;                                   // pixels for the text line at the bottom
    private static final double[] RATES = {0.125, 0.25, 0.5, 1, 2, 4, 8, Double.POSITIVE_INFINITY};   // playback speeds, infinity = no waiting
    private static LiveCityView lastOpened = NONE;                               // devtools restarts keep the JVM, so the old window gets closed first

    private final double mapWidth, mapHeight;
    private final double msPerNode, msPerStreet;
    private final Object lock = new Object();                                    // guards everything that gets drawn
    private final Object pauseLock = new Object();
    private JFrame frame;
    private JComponent canvas;

    // what gets drawn — plain arrays, so a 100,000-node city stays cheap
    private double[] nodes = new double[512];                                    // x, y per node
    private int nodeCount;
    private double[] streets = new double[1024];                                 // x1, y1, x2, y2 per street
    private int streetCount;
    private double[] squares = new double[0];                                    // x, y, size per leaf of the frozen tree
    private int squareCount;
    private Node current;                                                        // where the street being grown is right now
    private double searchRadius;
    private String phase = "nodes";

    // controls, changed from the window
    private volatile int rateIndex = 3;
    private volatile boolean paused, showSquares = true, closed;
    private double owedMs;                                                       // waiting not done yet — only the generator thread touches it

    private LiveCityView(double mapWidth, double mapHeight, double msPerNode, double msPerStreet) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.msPerNode = msPerNode;
        this.msPerStreet = msPerStreet;
    }

    // opens the window, or returns NONE when enabled is false or Java runs headless
    public static LiveCityView open(boolean enabled, double mapWidth, double mapHeight, double msPerNode, double msPerStreet) {
        if (!enabled) return NONE;
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("live view skipped: Java is running headless (RaggaApplication has to turn that off)");
            return NONE;
        }
        lastOpened.close();
        LiveCityView view = new LiveCityView(mapWidth, mapHeight, msPerNode, msPerStreet);
        try {
            SwingUtilities.invokeAndWait(view::buildWindow);
        } catch (Exception e) {
            throw new IllegalStateException("could not open the live view", e);
        }
        lastOpened = view;
        return view;
    }

    // a view without a window or waiting — collects the same data, for snapshot()
    public static LiveCityView offscreen(double mapWidth, double mapHeight) {
        return new LiveCityView(mapWidth, mapHeight, 0, 0);
    }

    // a node was added to the graph
    public void nodeAdded(Node node) {
        if (isOff()) return;
        synchronized (lock) {
            nodes = ensure(nodes, nodeCount * 2 + 2);
            nodes[nodeCount * 2] = node.getX();
            nodes[nodeCount * 2 + 1] = node.getY();
            nodeCount++;
        }
        repaint();
        pace(msPerNode);
    }

    // a street from a to b was added to the graph
    public void streetAdded(Node a, Node b) {
        if (isOff()) return;
        synchronized (lock) {
            streets = ensure(streets, streetCount * 4 + 4);
            int i = streetCount * 4;
            streets[i] = a.getX();
            streets[i + 1] = a.getY();
            streets[i + 2] = b.getX();
            streets[i + 3] = b.getY();
            streetCount++;
        }
        repaint();
        pace(msPerStreet);
    }

    // the street being grown has reached current — drawn with its search circle
    public void stepAt(Node current, double searchRadius) {
        if (isOff()) return;
        synchronized (lock) {
            this.current = current;
            this.searchRadius = searchRadius;
            phase = "streets";
        }
        repaint();
    }

    // the node phase is over — the frozen tree's leaves become the squares in the background
    public void treeBuilt(QuadTree tree) {
        if (isOff()) return;
        synchronized (lock) {
            squares = new double[tree.leaves().size() * 3];
            squareCount = 0;
            for (Leaf leaf : tree.leaves()) {
                squares[squareCount * 3] = leaf.x();
                squares[squareCount * 3 + 1] = leaf.y();
                squares[squareCount * 3 + 2] = leaf.size();
                squareCount++;
            }
            phase = "tree built";
        }
        repaint();
    }

    // generation is over
    public void done() {
        if (isOff()) return;
        synchronized (lock) {
            current = null;
            phase = "done";
        }
        repaint();
    }

    // draws the view as it is now into an image of w × h pixels
    public BufferedImage snapshot(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        draw(g, w, h);
        g.dispose();
        return image;
    }

    // draws everything scaled to fit w × h — map coordinates, y grows downwards like graph.png
    private void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, w, h);
        double scale = Math.min(w / mapWidth, (h - STATUS_BAR) / mapHeight);
        double ox = (w - mapWidth * scale) / 2;
        double oy = (h - STATUS_BAR - mapHeight * scale) / 2;
        String status;

        synchronized (lock) {
            if (showSquares) {
                g.setColor(SQUARE);
                g.setStroke(new BasicStroke(1f));
                Rectangle2D.Double box = new Rectangle2D.Double();
                for (int i = 0; i < squareCount * 3; i += 3) {
                    box.setRect(ox + squares[i] * scale, oy + squares[i + 1] * scale, squares[i + 2] * scale, squares[i + 2] * scale);
                    g.draw(box);
                }
            }

            Line2D.Double line = new Line2D.Double();
            g.setColor(STREET);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < streetCount * 4; i += 4) {
                line.setLine(ox + streets[i] * scale, oy + streets[i + 1] * scale, ox + streets[i + 2] * scale, oy + streets[i + 3] * scale);
                g.draw(line);
            }

            Ellipse2D.Double dot = new Ellipse2D.Double();
            double dotRadius = Math.clamp(scale * 3.3, 0.8, 2.0);               // smaller dots when a big city is zoomed out
            g.setColor(NODE);
            for (int i = 0; i < nodeCount * 2; i += 2) {
                dot.setFrame(ox + nodes[i] * scale - dotRadius, oy + nodes[i + 1] * scale - dotRadius, 2 * dotRadius, 2 * dotRadius);
                g.fill(dot);
            }

            // the newest street (or node, while there are no streets yet) stands out
            g.setColor(LATEST);
            if (streetCount > 0) {
                int i = (streetCount - 1) * 4;
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line.setLine(ox + streets[i] * scale, oy + streets[i + 1] * scale, ox + streets[i + 2] * scale, oy + streets[i + 3] * scale);
                g.draw(line);
            } else if (nodeCount > 0) {
                int i = (nodeCount - 1) * 2;
                dot.setFrame(ox + nodes[i] * scale - 4, oy + nodes[i + 1] * scale - 4, 8, 8);
                g.fill(dot);
            }

            if (current != null) {
                double cx = ox + current.getX() * scale, cy = oy + current.getY() * scale, r = searchRadius * scale;
                g.setColor(CURRENT);
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {5f, 4f}, 0f));
                g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
            }
            status = String.format("%s · %,d nodes · %,d streets", phase, nodeCount, streetCount);
        }

        double rate = RATES[rateIndex];
        String controls = (paused ? "PAUSED · " : "") + "speed " + (Double.isInfinite(rate) ? "max" : "×" + formatRate(rate))
                + " · space pause · +/- speed · Q squares";
        g.setColor(STREET);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString(status, 10, h - 10);
        g.drawString(controls, w - g.getFontMetrics().stringWidth(controls) - 10, h - 10);
    }

    // builds the window — runs on the Swing thread
    private void buildWindow() {
        canvas = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                draw((Graphics2D) g, getWidth(), getHeight());
            }
        };
        canvas.setPreferredSize(fitToScreen());
        frame = new JFrame("Ragga · live city");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closed = true;                                                   // generation carries on at full speed
                resume();
            }
        });
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                onKey(e);
            }
        });
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // space pauses, + and - change the speed, Q shows or hides the squares
    private void onKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_SPACE -> {
                paused = !paused;
                if (!paused) resume();
            }
            case KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> rateIndex = Math.min(rateIndex + 1, RATES.length - 1);
            case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> rateIndex = Math.max(rateIndex - 1, 0);
            case KeyEvent.VK_Q -> showSquares = !showSquares;
            default -> { return; }
        }
        repaint();
    }

    // waits as long as the speed asks, and for as long as the view is paused
    private void pace(double ms) {
        synchronized (pauseLock) {
            while (paused && !closed) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        owedMs += ms / RATES[rateIndex];
        if (owedMs < 15) return;                                                 // sleep in chunks: Windows timers are too coarse for 2 ms naps
        long start = System.nanoTime();
        try {
            Thread.sleep((long) owedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        owedMs = Math.max(owedMs - (System.nanoTime() - start) / 1e6, -15);    // oversleeping is paid back by the next events
    }

    private void resume() {
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    private void close() {
        closed = true;
        resume();
        if (frame != null) SwingUtilities.invokeLater(frame::dispose);
    }

    private boolean isOff() {
        return this == NONE || closed;
    }

    private void repaint() {
        if (canvas != null) canvas.repaint();
    }

    // a square window at 80% of the screen's height, shaped like the map
    private Dimension fitToScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double side = Math.min(screen.width, screen.height) * 0.8;
        double biggest = Math.max(mapWidth, mapHeight);
        return new Dimension((int) (side * mapWidth / biggest), (int) (side * mapHeight / biggest) + STATUS_BAR);
    }

    private static String formatRate(double rate) {
        return rate >= 1 ? String.valueOf((int) rate) : String.valueOf(rate);
    }

    private static double[] ensure(double[] array, int length) {
        return array.length >= length ? array : Arrays.copyOf(array, Math.max(length, array.length * 2));
    }
}
