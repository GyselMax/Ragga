package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;
import java.util.ArrayList;
import java.util.List;

public sealed interface QuadSquare permits Branch, Leaf {
    double x();                                                 // position from the top left
    double y();                                                 // position from the top left
    double size();                                              // width & height
    int nodeCount();

    // true if this square can reach the circle — its point closest to (x, y) is within radius
    default boolean intersectsWith(double x, double y, double radius) {
        double dx = x - Math.clamp(x, x(), x() + size());
        double dy = y - Math.clamp(y, y(), y() + size());
        return dx * dx + dy * dy <= radius * radius;            // Pythagoras theorem
    }


    // what you call: returns a new list
    default public List<Node> findWithin(double x, double y, double radius) {
        List<Node> result = new ArrayList<>();
        collectWithin(x, y, radius, result);
        return result;
    }

    void collectWithin(double x, double y, double radius, List<Node> result);
}
