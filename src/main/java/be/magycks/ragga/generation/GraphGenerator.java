package be.magycks.ragga.generation;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

@Component
public class GraphGenerator implements CommandLineRunner {
    // import constants
    @Value("${GENERATION_SEED}")
    private int GENERATION_SEED;
    @Value("${GRID_WIDTH}")
    private int GRID_WIDTH;
    @Value("${GRID_HEIGHT}")
    private int GRID_HEIGHT;
    @Value("${NODE_MINIMUM_DISTANCE}")
    private double NODE_MINIMUM_DISTANCE;
    @Value("${NODE_MAXIMUM_DISTANCE_VARIANCE}")
    private double NODE_MAXIMUM_DISTANCE_VARIANCE;
    @Value("${NODE_CANDIDATE_AMOUNT}")
    private int NODE_CANDIDATE_AMOUNT;
    @Value("${EDGE_ALIGNMENT_WEIGHT}")
    private double EDGE_ALIGNMENT_WEIGHT;
    @Value("${EDGE_DISTANCE_WEIGHT}")
    private double EDGE_DISTANCE_WEIGHT;
    @Value("${EDGE_SEARCH_RADIUS}")
    private int EDGE_SEARCH_RADIUS;
    private final double EDGE_MINIMUM_ANGLE = 30;
    private final int EDGE_MAXIMUM_DEGREE = 4;



    // run command
    @Override
    public void run(String @NonNull ... args) throws Exception {
        // initialize starter random and graph
        Random random = new Random(GENERATION_SEED);
        Graph<Node, DefaultEdge> graph = new SimpleWeightedGraph<>(DefaultEdge.class);

        // starter nodes aka seeds
        List<Node> seeds = List.of(
                new Node(750, 750, 5.5)
                //new Node(700, 500, 2.5),
                //new Node(500, 300, 2.5),
                //new Node(500, 700, 2.5)
        );

        // generate the queue & add the seeds to it
        Queue<Node> pending = new ArrayDeque<>();
        for (Node seed : seeds) {
            graph.addVertex(seed);
            pending.add(seed);
        }

        // growth loop — drains until nothing is left to split
        while (!pending.isEmpty()) {
            Node current = pending.poll();          // queue.poll() pulls & removes the first value
            ArrayList<Node> children = generateClusteredNodes(current, random, graph);

            for (Node child : children) {
                if (child.getSplitNumber() > 0) {
                    pending.add(child);
                }
            }
        }

        /* print out & draw each node
        System.out.println(graph.vertexSet().size());
        drawGraph(graph); */

        // add all the edges
        generateEdges(graph, random);

        // report & render
        System.out.println("nodes: " + graph.vertexSet().size());
        System.out.println("edges: " + graph.edgeSet().size());
        drawGraph(graph);
    }



    /* ALL NODE GENERATION DATA */
    // generate nodes dependent on the split number
    private ArrayList<Node> generateClusteredNodes(@NonNull Node node, Random random, @NonNull Graph<Node, DefaultEdge> graph) {
        // pull the values from the node
        double x = node.getX();
        double y = node.getY();
        double splitNumber = node.getSplitNumber();
        // initialize the array
        ArrayList<Node> generatedNodes = new ArrayList<>();

        // determine whether it actually splits
        if (splitNumber <= 0){throw new IllegalArgumentException("node has splitNumber 0");}
        if (splitNumber > 0 && splitNumber < 1 ){
            splitNumber = (random.nextDouble(1) > splitNumber) ? 0.0 : 1.0;
        }
        if (splitNumber == 0){return generatedNodes;}

        /* GENERATION ALGORITHM */

        // generate the angles and dist
        for (int i = 0; i < NODE_CANDIDATE_AMOUNT; i++) {
            double angle = random.nextDouble(2 * Math.PI);
            double distance = NODE_MINIMUM_DISTANCE + random.nextDouble(NODE_MAXIMUM_DISTANCE_VARIANCE);

            double candidateX = node.getX() + distance * Math.cos(angle);
            double candidateY = node.getY() + distance * Math.sin(angle);

            Node candidate = new Node(candidateX, candidateY, splitNumber - 1);

            // sanity check on the nodes & add it to pending & graph
            if (isWithinBounds(candidate) && isFarEnough(candidate, graph.vertexSet(), NODE_MINIMUM_DISTANCE)) {
                graph.addVertex(candidate);
                generatedNodes.add(candidate);
            }
        }



        return generatedNodes;
    }

    // checks minimum distance
    private boolean isFarEnough(@NonNull Node candidate, @NonNull Collection<Node> others, double minDistance) {
        double minSq = minDistance * minDistance;
        for (Node other : others) {
            double dx = candidate.getX() - other.getX();
            double dy = candidate.getY() - other.getY();
            if (dx * dx + dy * dy < minSq) return false;   // too close, reject immediately
        }
        return true;
    }

    // checks the node falls inside bounds
    private boolean isWithinBounds(@NonNull Node node) {
        return node.getX() >= 0 && node.getX() < GRID_WIDTH
                && node.getY() >= 0 && node.getY() < GRID_HEIGHT;
    }



    /* ALL EDGE GENERATION (mostly by claude) */

    /// EDGE GENERATION ITSELF ///
    // (MAIN CODE) - generates edges following the sanity checks
    private void generateEdges(@NonNull Graph<Node, DefaultEdge> graph, Random random) {
        // random start order, but driven by the seeded rng so runs stay reproducible
        List<Node> startingPoints = new ArrayList<>(graph.vertexSet());
        Collections.shuffle(startingPoints, random);

        for (Node start : startingPoints) {
            growStreet(graph, start);
        }
    }

    // extends one street node-by-node until no valid candidate is left
    private void growStreet(@NonNull Graph<Node, DefaultEdge> graph, @NonNull Node start) {
        Node previous = null;
        Node current = start;

        while (true) {
            Node best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Node candidate : graph.vertexSet()) {
                if (candidate == current) continue;
                if (!isWithinSearchRadius(current, candidate)) continue;
                if (!passesEdgeConstraints(graph, current, candidate)) continue;

                double score = scoreCandidate(previous, current, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            if (best == null) return;   // nothing valid nearby — the street ends here

            graph.addEdge(current, best);
            previous = current;
            current = best;
        }
    }

    // scores a candidate — higher is better
    private double scoreCandidate(Node previous, @NonNull Node current, @NonNull Node candidate) {
        double nextDx = candidate.getX() - current.getX();
        double nextDy = candidate.getY() - current.getY();
        double nextLength = Math.sqrt(nextDx * nextDx + nextDy * nextDy);

        // alignment: 1 = dead straight on, 0 = right angle, -1 = doubling back
        double alignment = 0;
        if (previous != null) {
            double prevDx = current.getX() - previous.getX();
            double prevDy = current.getY() - previous.getY();
            double prevLength = Math.sqrt(prevDx * prevDx + prevDy * prevDy);

            // dot product of the two unit vectors is cos(turn angle)
            alignment = (prevDx * nextDx + prevDy * nextDy) / (prevLength * nextLength);
        }

        // closeness: 1 = as short as possible, 0 = as long as possible
        double maxDistance = NODE_MINIMUM_DISTANCE + NODE_MAXIMUM_DISTANCE_VARIANCE;
        double closeness = 1 - (nextLength / maxDistance);

        return EDGE_ALIGNMENT_WEIGHT * alignment + EDGE_DISTANCE_WEIGHT * closeness;
    }

    // only nearby nodes are worth considering as the next step
    private boolean isWithinSearchRadius(@NonNull Node a, @NonNull Node b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        return dx * dx + dy * dy <= EDGE_SEARCH_RADIUS * EDGE_SEARCH_RADIUS;
    }


    /// SANITY CHECKS ///
    // (MAIN CODE) - every constraint, cheapest first so the expensive ones rarely run
    private boolean passesEdgeConstraints(@NonNull Graph<Node, DefaultEdge> graph, Node a, Node b) {
        return isDegreeWithinLimit(graph, a, b)
                && !graph.containsEdge(a, b)
                && isTriangleFree(graph, a, b)
                && isAngleWideEnough(graph, a, b)
                && isCrossingFree(graph, a, b);
    }

    // true if a->b crosses no existing edge
    private boolean isCrossingFree(@NonNull Graph<Node, DefaultEdge> graph, Node a, Node b) {
        for (DefaultEdge edge : graph.edgeSet()) {
            Node c = graph.getEdgeSource(edge);
            Node d = graph.getEdgeTarget(edge);

            // edges sharing an endpoint touch by design — not a crossing
            if (c == a || c == b || d == a || d == b) continue;

            if (Line2D.linesIntersect(a.getX(), a.getY(), b.getX(), b.getY(),
                    c.getX(), c.getY(), d.getX(), d.getY())) {
                return false;
            }
        }
        return true;
    }

    // true if a->b does not close a triangle
    private boolean isTriangleFree(@NonNull Graph<Node, DefaultEdge> graph, Node a, Node b) {
        return Collections.disjoint(Graphs.neighborSetOf(graph, a), Graphs.neighborSetOf(graph, b));
    }

    // true if a->b makes no tight angle with existing edges at either end
    private boolean isAngleWideEnough(@NonNull Graph<Node, DefaultEdge> graph, Node a, Node b) {
        double minRadians = Math.toRadians(EDGE_MINIMUM_ANGLE);

        // pass 0 checks around a, pass 1 around b
        for (int pass = 0; pass < 2; pass++) {
            // flips origin and target on loop
            Node origin = (pass == 0) ? a : b;
            Node target = (pass == 0) ? b : a;

            double newAngle = Math.atan2(target.getY() - origin.getY(), target.getX() - origin.getX());

            for (Node neighbour : Graphs.neighborListOf(graph, origin)) {
                double existingAngle = Math.atan2(neighbour.getY() - origin.getY(), neighbour.getX() - origin.getX());

                double difference = Math.abs(newAngle - existingAngle);
                if (difference > Math.PI) difference = 2 * Math.PI - difference;

                if (difference < minRadians) return false;
            }
        }
        return true;
    }

    // true if neither end would exceed the maximum number of streets at a junction
    private boolean isDegreeWithinLimit(@NonNull Graph<Node, DefaultEdge> graph, Node a, Node b) {
        return graph.degreeOf(a) < EDGE_MAXIMUM_DEGREE && graph.degreeOf(b) < EDGE_MAXIMUM_DEGREE;
    }


    /// RENDERER ///
    private void drawGraph(@NonNull Graph<Node, DefaultEdge> graph) throws IOException {
        BufferedImage image = new BufferedImage(GRID_WIDTH, GRID_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        final Color NODE_COLOR = new Color(100, 100, 100);

        // smooths the circle edges instead of letting them staircase
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background — without this the image starts black
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, GRID_WIDTH, GRID_HEIGHT);

        // nodes — ellipse takes the top-left corner, so offset by the radius to centre it
        double radius = 1;
        g2.setColor(NODE_COLOR);
        for (Node node : graph.vertexSet()) {
            g2.fill(new Ellipse2D.Double(node.getX() - radius, node.getY() - radius, radius * 2, radius * 2));
        }

        // edges
        g2.setColor(new Color(70, 70, 70));
        g2.setStroke(new BasicStroke(1.5f));
        for (DefaultEdge edge : graph.edgeSet()) {
            Node a = graph.getEdgeSource(edge);
            Node b = graph.getEdgeTarget(edge);
            g2.draw(new Line2D.Double(a.getX(), a.getY(), b.getX(), b.getY()));
        }

        g2.dispose();   // releases the graphics2d resources — do this before writing
        ImageIO.write(image, "png", new File("graph.png"));
    }
}
