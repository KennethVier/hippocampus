package com.hippocampus.materials.port;

import java.time.Instant;
import java.util.UUID;

public interface MaterialUploadPersistence {

    CreatedMaterial createInitialMaterial(InitialMaterial material);

    record InitialMaterial(
            UUID ownerId,
            String title,
            String materialType,
            String originalFilename,
            String mimeType,
            String storageKey,
            long fileSizeBytes) {}

    record CreatedMaterial(UUID materialId, UUID versionId, Instant createdAt) {}
}
