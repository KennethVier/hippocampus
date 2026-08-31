package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataSubtopicRepository extends JpaRepository<SubtopicEntity,UUID> {
    Optional<SubtopicEntity> findByIdAndTopicSubjectUserId(UUID id,UUID userId);
    Optional<SubtopicEntity> findByIdAndTopicSubjectUserIdAndTopicStatusAndTopicSubjectStatus(UUID id,UUID userId,
            LearningOrganizationStatus topicStatus,LearningOrganizationStatus subjectStatus);

    @Query(value="""
            SELECT st.* FROM subtopics st JOIN topics t ON t.id=st.topic_id JOIN subjects s ON s.id=t.subject_id
            WHERE st.topic_id=:topicId AND s.user_id=:ownerId AND s.status='ACTIVE'
              AND t.status='ACTIVE' AND st.status='ACTIVE'
            ORDER BY st.sort_order ASC NULLS LAST, lower(st.name) ASC, st.name ASC, st.id ASC
            """,countQuery="""
            SELECT count(*) FROM subtopics st JOIN topics t ON t.id=st.topic_id JOIN subjects s ON s.id=t.subject_id
            WHERE st.topic_id=:topicId AND s.user_id=:ownerId AND s.status='ACTIVE'
              AND t.status='ACTIVE' AND st.status='ACTIVE'
            """,nativeQuery=true)
    Page<SubtopicEntity> findActiveByOwnedActiveTopic(@Param("topicId") UUID topicId,
            @Param("ownerId") UUID ownerId,Pageable pageable);
}
