package com.hippocampus.materials.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDocumentNodeRepository extends JpaRepository<DocumentNodeEntity, UUID> {
    List<DocumentNodeEntity> findByMaterialVersionIdOrderByOrdinalAsc(UUID materialVersionId);

    List<DocumentNodeEntity> findByMaterialVersionIdAndParentIdOrderByOrdinalAsc(
            UUID materialVersionId, UUID parentId);
}
