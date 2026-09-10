package com.hippocampus.materials.api;

import java.time.Instant;
import java.util.UUID;

public record MaterialProcessingResponse(
        UUID materialId,
        UUID versionId,
        String status,
        double progress,
        String limitation,
        Instant updatedAt) {}