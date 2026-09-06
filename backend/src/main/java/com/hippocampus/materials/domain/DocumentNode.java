package com.hippocampus.materials.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentNode(
        UUID id,
        UUID materialVersionId,
        UUID parentId,
        DocumentNodeType nodeType,
        String title,
        Integer ordinal,
        Integer startPage,
        Integer endPage,
        Long startOffset,
        Long endOffset,
        DocumentNodeDetectionOrigin detectionOrigin,
        String detectionConfidence,
        Instant createdAt) {}
