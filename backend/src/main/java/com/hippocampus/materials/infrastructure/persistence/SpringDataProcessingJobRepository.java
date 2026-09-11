package com.hippocampus.materials.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataProcessingJobRepository extends JpaRepository<ProcessingJobEntity, UUID> {
}
