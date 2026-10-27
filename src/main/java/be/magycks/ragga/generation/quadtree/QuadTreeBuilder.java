package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;
import java.util.ArrayList;
import java.util.List;

public class QuadTreeBuilder {
    private static final int CAPACITY = 8;   // most nodes a leaf may hold before it splits
    private final double x, y, size;
    private List<Node> nodes = new ArrayList<>();
    private QuadTreeBuilder[] children;

    public QuadTreeBuilder(double x, double y, double size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    // adds a node to the leaf that covers its position — splits that leaf once it holds more than CAPACITY
    public void insert(Node node) {
        if (children != null){
            ChildWithNode(node).insert(node);                                    // function returns child and then instantly inserts the node, potentially chaining branches
            return;
        }
        nodes.add(node);
        if (nodes.size() > CAPACITY && size > 1) split();                       // size > 1: never split forever on stacked nodes
    }

    // returns every node within radius of (x, y) — map coordinates, a new list each call
    public List<Node> findWithin(double x, double y, double radius) {
        return List.of();
    }

    // adds every node within radius of (x, y) to result — the recursive part, skips squares that can't reach the circle
    public void collectWithin(double x, double y, double radius, List<Node> result) {

    }

    // freezes the tree into a QuadTree — the builder stays usable, later inserts don't reach the frozen copy
    public QuadTree build() {
        return null;
    }

    // turns this square into a Leaf or a Branch — collects the leaves in tree order on the way down
    private QuadSquare freeze(List<Leaf> leaves) {
        return null;
    }

    // turns this leaf into four children and moves its nodes down into them
    private void split() {

    }

    // picks the child square a node falls in — 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right (y grows downwards)
    private QuadTreeBuilder ChildWithNode(Node node) {
        int index = ((node.getX() >= x + (size / 2)) ? 1 : 0)                   // adds 1 if it's to the right
                  + ((node.getY() >= y + (size / 2)) ? 2 : 0);                  // adds 2 if it's on the bottom
        return children[index];
    }

}
