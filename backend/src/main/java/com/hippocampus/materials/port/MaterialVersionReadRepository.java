package com.hippocampus.materials.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MaterialVersionReadRepository {
    Optional<MaterialVersionSnapshot> findActiveByMaterialId(UUID materialId);

    record MaterialVersionSnapshot(
            UUID materialId,
            UUID versionId,
            String readiness,
            java.math.BigDecimal progress,
            String safeStage,
            String extractionQuality,
            Instant updatedAt) {}
}
