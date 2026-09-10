package com.hippocampus.materials.port;

import java.time.Instant;
import java.util.UUID;

public record MaterialMetadata(
        UUID id,
        String title,
        String materialType,
        String originalFilename,
        String mimeType,
        String status,
        UUID activeVersionId,
        Instant createdAt,
        Instant updatedAt) {}
