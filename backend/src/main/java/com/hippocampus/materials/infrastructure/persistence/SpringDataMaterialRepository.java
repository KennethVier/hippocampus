package com.hippocampus.materials.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataMaterialRepository extends JpaRepository<MaterialEntity, UUID> {

    Page<MaterialEntity> findByUserIdAndStatusNot(UUID userId, String status, Pageable pageable);

    Optional<MaterialEntity> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MaterialEntity material
               set material.status = 'DELETED', material.activeVersionId = null
             where material.id = :id
               and material.userId = :userId
            """)
    int markDeletedOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
