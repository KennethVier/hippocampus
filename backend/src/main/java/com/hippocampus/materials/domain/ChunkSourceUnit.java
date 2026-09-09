package com.hippocampus.materials.domain;
import java.util.Objects; import java.util.UUID;
public record ChunkSourceUnit(UUID textBlockId, UUID materialVersionId, UUID documentNodeId, int page,
        ChunkContentType contentType, TextBlockExtractionMethod extractionMethod, TextBlockQuality quality,
        String content, long sourceOrder, boolean fragment) {
    public ChunkSourceUnit { Objects.requireNonNull(textBlockId); Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId); Objects.requireNonNull(contentType); Objects.requireNonNull(extractionMethod);
        Objects.requireNonNull(content); if (page < 1 || sourceOrder < 1) throw new IllegalArgumentException("Invalid source position"); }
}
