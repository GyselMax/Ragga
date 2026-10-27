package be.magycks.ragga.generation.quadtree;

import be.magycks.ragga.generation.Node;

import java.util.List;

public record Branch(double x, double y, double size, List<QuadSquare> children) implements QuadSquare {

    // copies children to make sure they cannot change in memory later
    public Branch {
        children = List.copyOf(children);
        if (children.size() != 4) {
            throw new BranchDoesNotHave4ChildrenException("a branch needs 4 children, got " + children.size());
        }
    }

    // iterates through all possible leaves and adds relevant nodes
    @Override
    public int nodeCount() {
        int count = 0;
        for (QuadSquare child : children) count += child.nodeCount();
        return count;
    }

    // iterates through all possible leaves and adds relevant nodes
    @Override
    public void collectWithin(double x, double y, double radius, List<Node> result) {
        if (!intersectsWith(x, y, radius)) return;
        for (QuadSquare child : children) child.collectWithin(x, y, radius, result);
    }
}
