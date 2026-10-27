package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;

import java.util.List;

// a final quadtree — the top square to search from, plus every leaf to loop over
public record QuadTree(QuadSquare root, List<Leaf> leaves) {

    // copies the leaf list so the tree can't change afterward
    public QuadTree {
        leaves = List.copyOf(leaves);
    }

    // adds every node within radius of (x, y) to result — for loops that want to reuse one list
    public void collectWithin(double x, double y, double radius, List<Node> result) {
        root.collectWithin(x, y, radius, result);
    }

    // how many nodes the whole tree holds
    public int nodeCount() {
        return root.nodeCount();
    }
}
