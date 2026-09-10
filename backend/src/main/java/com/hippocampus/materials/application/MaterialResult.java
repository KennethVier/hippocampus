package com.hippocampus.materials.application;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.materials.port.MaterialMetadata;

public record MaterialResult(
        UUID id,
        String title,
        String materialType,
        String originalFilename,
        String mimeType,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static MaterialResult from(MaterialMetadata material) {
        return new MaterialResult(
                material.id(), material.title(), material.materialType(), material.originalFilename(),
                material.mimeType(), material.status(), material.createdAt(), material.updatedAt());
    }
}
