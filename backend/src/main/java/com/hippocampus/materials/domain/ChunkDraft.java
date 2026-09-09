package com.hippocampus.materials.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ChunkDraft(
        UUID id,
        UUID materialVersionId,
        UUID documentNodeId,
        int chunkIndex,
        String content,
        int tokenCount,
        int pageStart,
        int pageEnd,
        Set<Integer> primaryPages,
        List<String> headingPath,
        ChunkContentType contentType,
        TextBlockExtractionMethod extractionMethod,
        TextBlockQuality quality,
        long sourceOrder,
        List<SourceLink> sourceLinks,
        List<UUID> visualAssetIds) {

    public ChunkDraft {
        primaryPages = Set.copyOf(primaryPages);
        headingPath = List.copyOf(headingPath);
        sourceLinks = List.copyOf(sourceLinks);
        visualAssetIds = List.copyOf(visualAssetIds);
    }

    public record SourceLink(SourceTextBlockSnapshot source, int sourcePosition, boolean overlap) {
        public UUID textBlockId() { return source.id(); }
    }
}
