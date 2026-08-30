package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {
    Optional<SubjectEntity> findByIdAndUserId(UUID id, UUID userId);
}
