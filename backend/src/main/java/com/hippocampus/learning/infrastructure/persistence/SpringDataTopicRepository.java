package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataTopicRepository extends JpaRepository<TopicEntity,UUID> {
    Optional<TopicEntity> findByIdAndSubjectUserId(UUID id,UUID userId);
    Optional<TopicEntity> findByIdAndSubjectUserIdAndSubjectStatus(UUID id,UUID userId,LearningOrganizationStatus subjectStatus);
    Optional<TopicEntity> findByIdAndSubjectUserIdAndStatusAndSubjectStatus(UUID id,UUID userId,
            LearningOrganizationStatus status,LearningOrganizationStatus subjectStatus);

    @Query(value="""
            SELECT t.* FROM topics t JOIN subjects s ON s.id=t.subject_id
            WHERE t.subject_id=:subjectId AND s.user_id=:ownerId AND s.status='ACTIVE' AND t.status='ACTIVE'
            ORDER BY lower(t.name) ASC, t.name ASC, t.id ASC
            """,countQuery="""
            SELECT count(*) FROM topics t JOIN subjects s ON s.id=t.subject_id
            WHERE t.subject_id=:subjectId AND s.user_id=:ownerId AND s.status='ACTIVE' AND t.status='ACTIVE'
            """,nativeQuery=true)
    Page<TopicEntity> findActiveByOwnedActiveSubject(@Param("subjectId") UUID subjectId,
            @Param("ownerId") UUID ownerId,Pageable pageable);
}
