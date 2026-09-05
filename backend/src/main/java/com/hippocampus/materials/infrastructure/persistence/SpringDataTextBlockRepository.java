package com.hippocampus.materials.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTextBlockRepository extends JpaRepository<TextBlockEntity, UUID> {
    List<TextBlockEntity> findByMaterialVersionIdAndOrdinalBetweenOrderByOrdinalAsc(
            UUID materialVersionId, int firstOrdinal, int lastOrdinal);
}
