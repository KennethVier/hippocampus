package com.hippocampus.materials.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMaterialRepository extends JpaRepository<MaterialEntity, UUID> {

    Page<MaterialEntity> findByUserIdAndStatusNot(UUID userId, String status, Pageable pageable);

    Optional<MaterialEntity> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, String status);

    Optional<MaterialEntity> findByIdAndUserId(UUID id, UUID userId);
}
