package com.hippocampus.materials.api;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.materials.application.MaterialUploadResult;

public record MaterialUploadResponse(
        UUID materialId,
        UUID versionId,
        String title,
        String materialType,
        String originalFilename,
        String mimeType,
        long fileSizeBytes,
        String materialStatus,
        String processingStatus,
        Instant createdAt) {

    static MaterialUploadResponse from(MaterialUploadResult result) {
        return new MaterialUploadResponse(
                result.materialId(), result.versionId(), result.title(), result.materialType(), result.originalFilename(),
                result.mimeType(), result.fileSizeBytes(), result.materialStatus(), result.processingStatus(),
                result.createdAt());
    }
}
