package com.hippocampus.materials.api;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.materials.application.MaterialResult;

public record MaterialResponse(
        UUID id,
        String title,
        String materialType,
        String originalFilename,
        String mimeType,
        String status,
    UUID activeVersionId,
        Instant createdAt,
        Instant updatedAt) {

    static MaterialResponse from(MaterialResult result) {
        return new MaterialResponse(
                result.id(), result.title(), result.materialType(), result.originalFilename(),
        result.mimeType(), result.status(), result.activeVersionId(),
        result.createdAt(), result.updatedAt());
    }
}
