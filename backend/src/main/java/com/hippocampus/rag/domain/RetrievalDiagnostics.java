package com.hippocampus.rag.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RetrievalDiagnostics(
        int candidateCount,
        int selectedChunkCount,
        List<UUID> selectedChunkIds,
        List<UUID> selectedVisualIds,
        Set<UUID> sourceMaterialIds,
        Set<UUID> indexGenerationIds,
        RetrievalQuality retrievalQuality) {

    public RetrievalDiagnostics {
        if (candidateCount < 0 || selectedChunkCount < 0 || selectedChunkCount > candidateCount) {
            throw new IllegalArgumentException("diagnostic counts are inconsistent");
        }
        selectedChunkIds = immutableList(selectedChunkIds, "selectedChunkIds");
        selectedVisualIds = immutableList(selectedVisualIds, "selectedVisualIds");
        sourceMaterialIds = immutableSet(sourceMaterialIds, "sourceMaterialIds");
        indexGenerationIds = immutableSet(indexGenerationIds, "indexGenerationIds");
        Objects.requireNonNull(retrievalQuality, "retrievalQuality must not be null");
        if (selectedChunkCount != selectedChunkIds.size()) {
            throw new IllegalArgumentException("selectedChunkCount must equal selectedChunkIds size");
        }
        if (new LinkedHashSet<>(selectedChunkIds).size() != selectedChunkIds.size()
                || new LinkedHashSet<>(selectedVisualIds).size() != selectedVisualIds.size()) {
            throw new IllegalArgumentException("selected evidence IDs must be unique");
        }
    }

    private static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(name + " must not contain null");
        }
        return List.copyOf(values);
    }

    private static <T> Set<T> immutableSet(Set<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        LinkedHashSet<T> copied = new LinkedHashSet<>();
        for (T value : values) {
            copied.add(Objects.requireNonNull(value, name + " must not contain null"));
        }
        return Collections.unmodifiableSet(copied);
    }
}
