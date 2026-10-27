package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;
import java.util.List;

public record Leaf(double x, double y, double size, List<Node> nodes) implements QuadSquare {

    public Leaf {
        nodes = List.copyOf(nodes);
    }

    @Override
    public int nodeCount() {
        return nodes.size();
    }

    @Override
    public void collectWithin(double x, double y, double radius, List<Node> result) {
        if (!intersectsWith(x, y, radius)) return;
        for (Node node : nodes) {
            double dx = node.getX() - x;
            double dy = node.getY() - y;
            if (dx * dx + dy * dy <= radius * radius) result.add(node);
        }
    }
}
