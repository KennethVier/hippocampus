package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;

public interface MaterialVersionReadRepository {
    Optional<MaterialVersionSnapshot> findActiveOrLatestByMaterialId(UUID materialId);

    record MaterialVersionSnapshot(
            UUID materialId,
            UUID versionId,
            String readiness,
            java.math.BigDecimal progress,
            String extractionQuality) {}
}
