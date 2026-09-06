package com.hippocampus.materials.domain;

import java.time.Instant;
import java.util.UUID;

public record TextBlock(
        UUID id,
        UUID materialVersionId,
        UUID documentNodeId,
        Integer pageNumber,
        TextBlockType blockType,
        int ordinal,
        String content,
        TextBlockExtractionMethod extractionMethod,
        TextBlockQuality quality,
        Instant createdAt) {}
