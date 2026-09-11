package com.hippocampus.materials.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.materials.port.MaterialVersionReadRepository;

public final class JpaMaterialVersionReadRepository implements MaterialVersionReadRepository {
    private final SpringDataMaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;

    public JpaMaterialVersionReadRepository(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        this.materials = materials;
        this.versions = versions;
    }

    @Override
    public Optional<MaterialVersionSnapshot> findActiveOrLatestByMaterialId(UUID materialId) {
        UUID activeVersionId = materials.findById(materialId)
                .map(MaterialEntity::getActiveVersionId)
                .orElse(null);

        UUID versionId = activeVersionId != null
                ? activeVersionId
                : versions.findFirstByMaterialIdOrderByVersionNumberDesc(materialId)
                        .map(MaterialVersionEntity::getId)
                        .orElse(null);

        if (versionId == null) {
            return Optional.empty();
        }

        return versions.findById(versionId)
                .map(version -> new MaterialVersionSnapshot(
                        materialId,
                        version.getId(),
                        version.getProcessingStatus(),
                        version.getProcessingProgress(),
                        version.getExtractionQuality()));
    }
}
