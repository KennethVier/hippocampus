package com.hippocampus.materials.api;

import java.util.UUID;

public record MaterialProcessingResponse(
        UUID materialId,
        UUID versionId,
        String readiness,
        String stage,
        Double progress,
        String limitation,
        boolean structureAvailable) {}
