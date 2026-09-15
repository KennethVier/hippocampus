package com.hippocampus.rag.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EvidenceVisual(
        UUID visualId,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        String visualType,
        String caption,
        String nearbyText,
        String interpretationStatus,
        Set<UUID> linkedChunkIds,
        Set<String> relationshipTypes) {

    public EvidenceVisual {
        Objects.requireNonNull(visualId, "visualId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        Objects.requireNonNull(visualType, "visualType must not be null");
        Objects.requireNonNull(interpretationStatus, "interpretationStatus must not be null");
        linkedChunkIds = immutableNonEmptySet(linkedChunkIds, "linkedChunkIds");
        relationshipTypes = immutableNonEmptySet(relationshipTypes, "relationshipTypes");
    }

    private static <T> Set<T> immutableNonEmptySet(Set<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        LinkedHashSet<T> copied = new LinkedHashSet<>();
        for (T value : values) {
            copied.add(Objects.requireNonNull(value, name + " must not contain null"));
        }
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Collections.unmodifiableSet(copied);
    }
}
