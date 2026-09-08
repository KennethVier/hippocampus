package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.port.BinaryObjectKey;

public record VisualAssetDraft(
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        BinaryObjectKey storageKey,
        VisualType visualType,
        String caption,
        String nearbyText,
        VisualInterpretationStatus interpretationStatus,
        int widthPixels,
        int heightPixels,
        String contentHash) {
    public VisualAssetDraft {
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(visualType);
        Objects.requireNonNull(interpretationStatus);
        if (pageNumber < 1 || widthPixels < 1 || heightPixels < 1) {
            throw new IllegalArgumentException("Visual page and dimensions must be positive");
        }
        if (!Objects.requireNonNull(contentHash).matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Visual content hash must be lowercase SHA-256");
        }
    }
}
