package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import com.hippocampus.learning.domain.Topic;
import com.hippocampus.learning.domain.TopicStatus;
import com.hippocampus.learning.port.TopicPage;
import com.hippocampus.learning.port.TopicPageRequest;
import com.hippocampus.learning.port.TopicRepository;

@Repository
@Lazy
public class JpaTopicRepository implements TopicRepository {
    private final SpringDataTopicRepository topics;
    private final SpringDataSubjectRepository subjects;
    public JpaTopicRepository(SpringDataTopicRepository topics,SpringDataSubjectRepository subjects) {
        this.topics=topics; this.subjects=subjects;
    }

    @Override public Optional<Topic> createUnderActiveOwnedSubject(Topic topic,UUID ownerId) {
        return subjects.findByIdAndUserIdAndStatus(topic.subjectId(),ownerId,LearningOrganizationStatus.ACTIVE)
                .map(subject -> {
                    TopicEntity entity=new TopicEntity(subject,topic.name(),toPersistence(topic.status()));
                    entity.setDescription(topic.description());
                    return toDomain(topics.saveAndFlush(entity));
                });
    }

    @Override public Optional<Topic> findOwnedById(UUID topicId,UUID ownerId) {
        return topics.findByIdAndSubjectUserId(topicId,ownerId).map(JpaTopicRepository::toDomain);
    }

    @Override public Optional<Topic> findOwnedByIdWithActiveSubject(UUID topicId,UUID ownerId) {
        return topics.findByIdAndSubjectUserIdAndSubjectStatus(topicId,ownerId,LearningOrganizationStatus.ACTIVE)
                .map(JpaTopicRepository::toDomain);
    }

    @Override public Optional<Topic> saveOwned(Topic topic,UUID ownerId) {
        return topics.findByIdAndSubjectUserId(topic.id(),ownerId).map(entity -> {
            entity.setName(topic.name()); entity.setDescription(topic.description());
            entity.setStatus(toPersistence(topic.status()));
            return toDomain(topics.saveAndFlush(entity));
        });
    }

    @Override public Optional<TopicPage> findActiveByOwnedActiveSubject(UUID subjectId,UUID ownerId,TopicPageRequest request) {
        if (subjects.findByIdAndUserIdAndStatus(subjectId,ownerId,LearningOrganizationStatus.ACTIVE).isEmpty()) return Optional.empty();
        Page<TopicEntity> page=topics.findActiveByOwnedActiveSubject(subjectId,ownerId,PageRequest.of(request.page(),request.size()));
        return Optional.of(new TopicPage(page.getContent().stream().map(JpaTopicRepository::toDomain).toList(),
                page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages()));
    }

    private static Topic toDomain(TopicEntity entity) {
        return new Topic(entity.getId(),entity.getSubject().getId(),entity.getName(),entity.getDescription(),
                TopicStatus.valueOf(entity.getStatus().name()),entity.getCreatedAt(),entity.getUpdatedAt());
    }
    private static LearningOrganizationStatus toPersistence(TopicStatus status) {
        return LearningOrganizationStatus.valueOf(status.name());
    }
}
