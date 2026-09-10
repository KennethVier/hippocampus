package com.hippocampus.materials.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hippocampus.materials.domain.DocumentNodeType;

public interface SpringDataDocumentNodeRepository extends JpaRepository<DocumentNodeEntity, UUID> {
    boolean existsByMaterialVersionIdAndNodeTypeAndParentIdIsNull(
            UUID materialVersionId, DocumentNodeType nodeType);

    Optional<DocumentNodeEntity> findByMaterialVersionIdAndNodeTypeAndParentIdIsNull(
            UUID materialVersionId, DocumentNodeType nodeType);

    List<DocumentNodeEntity> findByMaterialVersionIdOrderByOrdinalAsc(UUID materialVersionId);

    List<DocumentNodeEntity> findByMaterialVersionIdAndParentIdOrderByOrdinalAsc(
            UUID materialVersionId, UUID parentId);
}
