package com.hippocampus.materials.application;

import java.time.Instant;
import java.util.UUID;

public record MaterialUploadResult(
        UUID materialId,
        UUID versionId,
        String title,
        String materialType,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        String materialStatus,
        String processingStatus,
        Instant createdAt) {}
