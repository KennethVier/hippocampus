package com.hippocampus.materials.domain;
import java.util.List; import java.util.UUID;
public record ChunkDraft(UUID id, UUID materialVersionId, UUID documentNodeId, int chunkIndex, String content,
        int tokenCount, int pageStart, int pageEnd, List<String> headingPath, ChunkContentType contentType,
        TextBlockExtractionMethod extractionMethod, TextBlockQuality quality, long sourceOrder,
        List<SourceLink> sourceLinks, List<UUID> visualAssetIds) {
    public record SourceLink(UUID textBlockId, int sourcePosition, boolean overlap) {}
}
