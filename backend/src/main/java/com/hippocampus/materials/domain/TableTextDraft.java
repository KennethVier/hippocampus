package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record TableTextDraft(
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        int ordinal,
        String content,
        TextBlockExtractionMethod extractionMethod,
        TextBlockQuality quality) {
    public TableTextDraft {
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId);
        Objects.requireNonNull(content);
        Objects.requireNonNull(extractionMethod);
        Objects.requireNonNull(quality);
        if (pageNumber < 1 || ordinal < 1 || content.isBlank()) {
            throw new IllegalArgumentException("Table text location and content must be valid");
        }
        if (extractionMethod == TextBlockExtractionMethod.NATIVE && quality == TextBlockQuality.POOR) {
            throw new IllegalArgumentException("Native table quality cannot be POOR");
        }
        if (extractionMethod == TextBlockExtractionMethod.OCR && quality == TextBlockQuality.STRONG) {
            throw new IllegalArgumentException("OCR table quality cannot be STRONG");
        }
    }
}
