package com.hippocampus.rag.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RetrievalScopeTarget(UUID materialVersionId, Set<UUID> documentNodeIds) {
    public RetrievalScopeTarget {
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        documentNodeIds = Set.copyOf(Objects.requireNonNull(
                documentNodeIds, "documentNodeIds must not be null"));
    }

    public boolean allowsWholeMaterialVersion() {
        return documentNodeIds.isEmpty();
    }
}
