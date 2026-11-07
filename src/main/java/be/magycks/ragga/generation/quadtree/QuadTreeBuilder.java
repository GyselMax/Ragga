package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;
import java.util.ArrayList;
import java.util.List;

public class QuadTreeBuilder {
    private static final int CAPACITY = 8;      // most nodes a leaf may hold before it splits
    private final double x, y, size;            // the left corner coordinates
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
            ChildWithNode(node).insert(node);                                   // function returns child and then instantly inserts the node, potentially chaining branches
            return;
        }
        nodes.add(node);
        if (nodes.size() > CAPACITY && size > 1) split();                       // size > 1: never split forever on stacked nodes
    }


    /// NODE SEARCH FUNCTION ///
    // returns every node within radius of (x, y) — map coordinates, a new list each call
    public List<Node> findWithin(double circleCenterX, double circleCenterY, double radius) {
        List<Node> result = new ArrayList<>();
        collectWithin(circleCenterX, circleCenterY, radius, result);
        return result;
    }

    // adds every node within radius of (x, y) to result — the recursive part, skips squares that can't reach the circle
    public void collectWithin(double circleCenterX, double circleCenterY, double radius, List<Node> result) {
        // the point of this square closest to (x, y): if even that is too far, skip the whole square
        double dx = circleCenterX - Math.clamp(circleCenterX, this.x, this.x + size);
        double dy = circleCenterY - Math.clamp(circleCenterY, this.y, this.y + size);
        if (dx * dx + dy * dy > radius * radius) return;

        // checks if the QuadSquare is a branch - if so loops through the children, potentially recursively
        if (children != null) {
            for (QuadTreeBuilder child : children) child.collectWithin(circleCenterX, circleCenterY, radius, result);
            return;
        }
        // checks if the nodes are in the circle
        for (Node node : nodes) {
            double ndx = node.getX() - circleCenterX;
            double ndy = node.getY() - circleCenterY;
            if (ndx * ndx + ndy * ndy <= radius * radius) result.add(node);
        }
    }


    /// CONVERT AND FINALIZE ///
    // freezes the tree into a QuadTree — the builder stays usable, later inserts don't reach the frozen copy
    public QuadTree build() {
        List<Leaf> leaves = new ArrayList<>();
        QuadSquare root = freeze(leaves);
        return new QuadTree(root, leaves);
    }


    /// PRIVATE HELPER FUNCTIONS ///
    // turns this square into a Leaf or a Branch — collects the leaves in tree order on the way down
    private QuadSquare freeze(List<Leaf> leaves) {
        // returns leaf
        if (children == null) {
            Leaf leaf = new Leaf(x, y, size, nodes);
            leaves.add(leaf);
            return leaf;
        }
        // returns branch
        List<QuadSquare> frozen = new ArrayList<>(4);
        for (QuadTreeBuilder child : children) frozen.add(child.freeze(leaves));
        return new Branch(x, y, size, frozen);
    }

    // turns this leaf into four children and moves its nodes down into them
    private void split() {
        double half = size / 2;
        children = new QuadTreeBuilder[] {
                new QuadTreeBuilder(x, y, half),          new QuadTreeBuilder(x + half, y, half),
                new QuadTreeBuilder(x, y + half, half),new QuadTreeBuilder(x + half, y + half, half)
        };
        for (Node node : nodes) ChildWithNode(node).insert(node);
        nodes = null;   // a split square keeps no nodes of its own
    }

    // picks the child square a node falls in — 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right (y grows downwards)
    private QuadTreeBuilder ChildWithNode(Node node) {
        int index = ((node.getX() >= x + (size / 2)) ? 1 : 0)                   // adds 1 if it's to the right
                  + ((node.getY() >= y + (size / 2)) ? 2 : 0);                  // adds 2 if it's on the bottom
        return children[index];
    }

}