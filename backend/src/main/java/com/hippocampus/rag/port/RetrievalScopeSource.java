package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record RetrievalScopeSource(UUID materialVersionId, UUID documentNodeId) {
    public RetrievalScopeSource {
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
    }
}
