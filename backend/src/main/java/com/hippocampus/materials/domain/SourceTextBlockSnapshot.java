package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record SourceTextBlockSnapshot(
        UUID id,
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        TextBlockType blockType,
        int ordinal,
        String rawContent,
        String normalizedContent,
        TextBlockExtractionMethod extractionMethod,
        TextBlockQuality quality) {

    public SourceTextBlockSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId);
        Objects.requireNonNull(blockType);
        Objects.requireNonNull(rawContent);
        Objects.requireNonNull(normalizedContent);
        Objects.requireNonNull(extractionMethod);
        if (pageNumber < 1 || ordinal < 1) {
            throw new IllegalArgumentException("Source page and ordinal must be positive");
        }
    }

    public static SourceTextBlockSnapshot from(TextBlock block) {
        return new SourceTextBlockSnapshot(block.id(), block.materialVersionId(), block.documentNodeId(),
                block.pageNumber(), block.blockType(), block.ordinal(), block.content(), block.normalizedContent(),
                block.extractionMethod(), block.quality());
    }
}
