package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataSubjectRepository extends JpaRepository<SubjectEntity, UUID> {
    Optional<SubjectEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<SubjectEntity> findByIdAndUserIdAndStatus(UUID id, UUID userId, LearningOrganizationStatus status);

    @Query(value="SELECT id FROM subjects WHERE id=:id AND user_id=:owner AND status='ACTIVE' FOR SHARE NOWAIT",nativeQuery=true)
    Optional<UUID> lockActiveForMissionStart(@Param("id") UUID id,@Param("owner") UUID owner);

    @Query(value = """
            SELECT * FROM subjects
            WHERE user_id = :userId AND status = 'ACTIVE'
            ORDER BY sort_order ASC NULLS LAST, lower(name) ASC, id ASC
            """, countQuery = """
            SELECT count(*) FROM subjects
            WHERE user_id = :userId AND status = 'ACTIVE'
            """, nativeQuery = true)
    Page<SubjectEntity> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);
}
