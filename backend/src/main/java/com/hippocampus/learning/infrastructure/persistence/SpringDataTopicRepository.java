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

    @Query(value="SELECT t.subject_id FROM topics t JOIN subjects s ON s.id=t.subject_id WHERE t.id=:id AND s.user_id=:owner",nativeQuery=true)
    Optional<UUID> findOwnedParentId(@Param("id") UUID id,@Param("owner") UUID owner);

    @Query(value="""
        SELECT t.id AS id,t.subject_id AS subjectId,t.name AS name,t.description AS description,
               t.status AS status,t.created_at AS createdAt,t.updated_at AS updatedAt
        FROM topics t WHERE t.id=:id AND t.subject_id=:subject AND t.status='ACTIVE'
          AND EXISTS(SELECT 1 FROM subjects s WHERE s.id=t.subject_id AND s.user_id=:owner AND s.status='ACTIVE')
        FOR SHARE OF t NOWAIT
        """,nativeQuery=true)
    Optional<LockedTopic> lockActiveForMissionStart(@Param("id") UUID id,@Param("subject") UUID subject,
                                                   @Param("owner") UUID owner);
    interface LockedTopic {
        UUID getId(); UUID getSubjectId(); String getName(); String getDescription(); String getStatus();
        java.time.Instant getCreatedAt(); java.time.Instant getUpdatedAt();
    }

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
