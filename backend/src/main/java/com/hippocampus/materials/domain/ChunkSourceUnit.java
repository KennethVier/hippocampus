package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record ChunkSourceUnit(
        SourceTextBlockSnapshot source,
        ChunkContentType contentType,
        String content,
        long sourceOrder,
        boolean fragment) {

    public ChunkSourceUnit {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (sourceOrder < 1) {
            throw new IllegalArgumentException("sourceOrder must be positive");
        }
    }

    public UUID textBlockId() { return source.id(); }
    public UUID materialVersionId() { return source.materialVersionId(); }
    public UUID documentNodeId() { return source.documentNodeId(); }
    public int page() { return source.pageNumber(); }
    public TextBlockExtractionMethod extractionMethod() { return source.extractionMethod(); }
    public TextBlockQuality quality() { return source.quality(); }
}
