package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LexicalSearchScope(
        UUID userId,
        Set<UUID> allowedMaterialVersionIds,
        Set<UUID> allowedDocumentNodeIds) {

    public LexicalSearchScope {
        Objects.requireNonNull(userId, "userId must not be null");
        allowedMaterialVersionIds = Set.copyOf(Objects.requireNonNull(
                allowedMaterialVersionIds, "allowedMaterialVersionIds must not be null"));
        allowedDocumentNodeIds = Set.copyOf(Objects.requireNonNull(
                allowedDocumentNodeIds, "allowedDocumentNodeIds must not be null"));
    }
}
