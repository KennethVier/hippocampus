package com.hippocampus.materials.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DocumentNodePageLocator {
    private final DocumentNode root;
    private final List<NodeDepth> nodes;

    public DocumentNodePageLocator(UUID materialVersionId, List<DocumentNode> nodes) {
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Map<UUID, DocumentNode> byId = new HashMap<>();
        List<DocumentNode> durableNodes = List.copyOf(Objects.requireNonNull(nodes));
        for (DocumentNode node : durableNodes) {
            if (!materialVersionId.equals(node.materialVersionId()) || byId.put(node.id(), node) != null) {
                throw new IllegalStateException("Document hierarchy does not belong exclusively to the material version");
            }
        }
        List<DocumentNode> roots = durableNodes.stream()
                .filter(node -> node.nodeType() == DocumentNodeType.DOCUMENT && node.parentId() == null)
                .toList();
        if (roots.size() != 1) {
            throw new IllegalStateException("Exactly one durable document root is required");
        }
        root = roots.getFirst();
        Map<UUID, Integer> depths = new HashMap<>();
        this.nodes = durableNodes.stream()
                .map(node -> new NodeDepth(node, depth(node, byId, depths, new HashSet<>())))
                .toList();
    }

    public UUID locate(int pageNumber) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        return nodes.stream()
                .filter(candidate -> contains(candidate.node(), pageNumber))
                .max(Comparator.comparingInt(NodeDepth::depth)
                        .thenComparingInt(candidate -> -range(candidate.node()))
                        .thenComparingInt(candidate -> nullable(candidate.node().startPage()))
                        .thenComparingInt(candidate -> nullable(candidate.node().ordinal())))
                .map(candidate -> candidate.node().id())
                .orElse(root.id());
    }

    private static boolean contains(DocumentNode node, int page) {
        return node.startPage() != null && node.endPage() != null
                && node.startPage() <= page && page <= node.endPage();
    }

    private static int range(DocumentNode node) {
        return node.startPage() == null || node.endPage() == null
                ? Integer.MAX_VALUE : node.endPage() - node.startPage();
    }

    private static int nullable(Integer value) {
        return value == null ? Integer.MIN_VALUE : value;
    }

    private static int depth(
            DocumentNode node,
            Map<UUID, DocumentNode> nodes,
            Map<UUID, Integer> depths,
            Set<UUID> visiting) {
        Integer known = depths.get(node.id());
        if (known != null) {
            return known;
        }
        if (!visiting.add(node.id())) {
            throw new IllegalStateException("Document hierarchy must be acyclic");
        }
        int depth;
        if (node.parentId() == null) {
            depth = 0;
        } else {
            DocumentNode parent = nodes.get(node.parentId());
            if (parent == null) {
                throw new IllegalStateException("Document hierarchy contains a missing parent");
            }
            depth = Math.addExact(depth(parent, nodes, depths, visiting), 1);
        }
        visiting.remove(node.id());
        depths.put(node.id(), depth);
        return depth;
    }

    private record NodeDepth(DocumentNode node, int depth) {}
}
