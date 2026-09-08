package com.hippocampus.materials.domain;

import java.util.Objects;

public record ExtractedPdfTable(String content, TextBlockQuality quality) {
    public ExtractedPdfTable {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(quality, "quality must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (quality == TextBlockQuality.POOR) {
            throw new IllegalArgumentException("Native table quality cannot be POOR");
        }
    }
}
