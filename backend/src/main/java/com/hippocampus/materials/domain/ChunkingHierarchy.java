package com.hippocampus.materials.domain;

import java.util.ArrayList; import java.util.HashMap; import java.util.HashSet; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.Set; import java.util.UUID;

public final class ChunkingHierarchy {
    private final UUID version; private final Map<UUID, DocumentNode> nodes; private final Map<UUID,List<String>> paths;
    public ChunkingHierarchy(UUID version, int pageCount, List<DocumentNode> input, int maxNodes, int maxDepth) {
        this.version = Objects.requireNonNull(version); Objects.requireNonNull(input);
        if (pageCount < 1 || maxNodes < 1 || maxDepth < 1 || input.size() > maxNodes) throw new IllegalArgumentException("Invalid hierarchy bounds");
        nodes = new HashMap<>(); int roots = 0;
        for (DocumentNode n : input) {
            if (!version.equals(n.materialVersionId()) || nodes.put(n.id(), n) != null || n.startPage() == null || n.endPage() == null
                    || n.startPage() < 1 || n.endPage() < n.startPage() || n.endPage() > pageCount) throw new IllegalArgumentException("Invalid chunk hierarchy");
            if (n.nodeType() == DocumentNodeType.DOCUMENT && n.parentId() == null) roots++;
        }
        if (roots != 1) throw new IllegalArgumentException("Hierarchy requires exactly one document root");
        paths = new HashMap<>(); for (DocumentNode n : input) path(n.id(), new HashSet<>(), maxDepth);
    }
    public void validate(UUID nodeId, int page) { DocumentNode n = nodes.get(nodeId);
        if (n == null || page < n.startPage() || page > n.endPage()) throw new IllegalArgumentException("Source block is outside its hierarchy node"); }
    public List<String> headingPath(UUID nodeId) { List<String> result = paths.get(nodeId); if (result == null) throw new IllegalArgumentException("Unknown hierarchy node"); return result; }
    private List<String> path(UUID id, Set<UUID> visiting, int maxDepth) {
        List<String> known = paths.get(id); if (known != null) return known;
        if (!visiting.add(id) || visiting.size() > maxDepth) throw new IllegalArgumentException("Hierarchy cycle or depth limit");
        DocumentNode n = nodes.get(id); if (n == null) throw new IllegalArgumentException("Missing hierarchy parent");
        List<String> result = n.parentId() == null ? new ArrayList<>() : new ArrayList<>(path(n.parentId(), visiting, maxDepth));
        if (n.title() != null && !n.title().isBlank()) result.add(n.title());
        visiting.remove(id); result = List.copyOf(result); paths.put(id, result); return result;
    }
}
