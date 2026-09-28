package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataActivitySourceReferenceRepository
        extends JpaRepository<ActivitySourceReferenceEntity, ActivitySourceReferenceId> {
    @Query("""
            SELECT link
            FROM ActivitySourceReferenceEntity link
            WHERE link.id.learningActivityId = :learningActivityId
            """)
    List<ActivitySourceReferenceEntity> findAllByLearningActivityId(
            @Param("learningActivityId") UUID learningActivityId);
}
