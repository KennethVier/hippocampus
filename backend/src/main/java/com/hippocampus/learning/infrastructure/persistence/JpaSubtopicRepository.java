package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import com.hippocampus.learning.domain.Subtopic;
import com.hippocampus.learning.domain.SubtopicStatus;
import com.hippocampus.learning.port.SubtopicPage;
import com.hippocampus.learning.port.SubtopicPageRequest;
import com.hippocampus.learning.port.SubtopicRepository;

@Repository
@Lazy
public class JpaSubtopicRepository implements SubtopicRepository {
    private final SpringDataSubtopicRepository subtopics; private final SpringDataTopicRepository topics;
    public JpaSubtopicRepository(SpringDataSubtopicRepository subtopics,SpringDataTopicRepository topics) {
        this.subtopics=subtopics; this.topics=topics;
    }

    @Override public Optional<Subtopic> createUnderActiveOwnedTopic(Subtopic subtopic,UUID ownerId) {
        return topics.findByIdAndSubjectUserIdAndStatusAndSubjectStatus(subtopic.topicId(),ownerId,
                LearningOrganizationStatus.ACTIVE,LearningOrganizationStatus.ACTIVE).map(topic -> {
                    SubtopicEntity entity=new SubtopicEntity(topic,subtopic.name(),toPersistence(subtopic.status()));
                    entity.setDescription(subtopic.description()); entity.setSortOrder(subtopic.sortOrder());
                    return toDomain(subtopics.saveAndFlush(entity));
                });
    }

    @Override public Optional<Subtopic> findOwnedById(UUID subtopicId,UUID ownerId) {
        return subtopics.findByIdAndTopicSubjectUserId(subtopicId,ownerId).map(JpaSubtopicRepository::toDomain);
    }

    @Override public Optional<Subtopic> findOwnedByIdWithActiveAncestors(UUID subtopicId,UUID ownerId) {
        return subtopics.findByIdAndTopicSubjectUserIdAndTopicStatusAndTopicSubjectStatus(subtopicId,ownerId,
                LearningOrganizationStatus.ACTIVE,LearningOrganizationStatus.ACTIVE).map(JpaSubtopicRepository::toDomain);
    }

    @Override public Optional<Subtopic> saveOwned(Subtopic subtopic,UUID ownerId) {
        return subtopics.findByIdAndTopicSubjectUserId(subtopic.id(),ownerId).map(entity -> {
            entity.setName(subtopic.name()); entity.setDescription(subtopic.description()); entity.setSortOrder(subtopic.sortOrder());
            entity.setStatus(toPersistence(subtopic.status())); return toDomain(subtopics.saveAndFlush(entity));
        });
    }

    @Override public Optional<SubtopicPage> findActiveByOwnedActiveTopic(UUID topicId,UUID ownerId,SubtopicPageRequest request) {
        if (topics.findByIdAndSubjectUserIdAndStatusAndSubjectStatus(topicId,ownerId,
                LearningOrganizationStatus.ACTIVE,LearningOrganizationStatus.ACTIVE).isEmpty()) return Optional.empty();
        Page<SubtopicEntity> page=subtopics.findActiveByOwnedActiveTopic(topicId,ownerId,PageRequest.of(request.page(),request.size()));
        return Optional.of(new SubtopicPage(page.getContent().stream().map(JpaSubtopicRepository::toDomain).toList(),
                page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages()));
    }

    private static Subtopic toDomain(SubtopicEntity entity) {
        return new Subtopic(entity.getId(),entity.getTopic().getId(),entity.getName(),entity.getDescription(),entity.getSortOrder(),
                SubtopicStatus.valueOf(entity.getStatus().name()),entity.getCreatedAt(),entity.getUpdatedAt());
    }
    private static LearningOrganizationStatus toPersistence(SubtopicStatus status) {
        return LearningOrganizationStatus.valueOf(status.name());
    }
}
