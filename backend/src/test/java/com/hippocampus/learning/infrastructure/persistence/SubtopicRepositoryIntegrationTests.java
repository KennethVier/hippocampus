package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

import jakarta.persistence.EntityManager;

class SubtopicRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException { resetPostgresSchema(); }

    @Test
    void createsReadsUpdatesAndPersistsBothLifecycleStates() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subtopic-crud");
            var subject = context.getBean(SubjectRepository.class).saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var topic = context.getBean(TopicRepository.class).saveAndFlush(new TopicEntity(
                    subject, "Thorax", LearningOrganizationStatus.ACTIVE));
            var subtopics = context.getBean(SubtopicRepository.class);
            var subtopic = new SubtopicEntity(topic, "Heart", LearningOrganizationStatus.ACTIVE);
            subtopic.setDescription(null);
            subtopic.setSortOrder(null);
            subtopic = subtopics.saveAndFlush(subtopic);

            assertThat(subtopic.getId()).isNotNull();
            assertThat(subtopic.getTopic().getId()).isEqualTo(topic.getId());
            assertThat(subtopic.getCreatedAt()).isNotNull();
            assertThat(subtopic.getUpdatedAt()).isNotNull();

            var id = subtopic.getId();
            var createdAt = subtopic.getCreatedAt();
            var previousUpdatedAt = subtopic.getUpdatedAt();
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var entityManager = context.getBean(EntityManager.class);
            transactions.executeWithoutResult(ignored -> {
                var managed = subtopics.findById(id).orElseThrow();
                managed.setName("Cardiac anatomy");
                managed.setDescription("Heart structures");
                managed.setSortOrder(3);
                managed.setStatus(LearningOrganizationStatus.ARCHIVED);
                subtopics.flush();
                entityManager.clear();
                var reloaded = subtopics.findById(id).orElseThrow();
                assertThat(reloaded.getName()).isEqualTo("Cardiac anatomy");
                assertThat(reloaded.getDescription()).isEqualTo("Heart structures");
                assertThat(reloaded.getSortOrder()).isEqualTo(3);
                assertThat(reloaded.getStatus()).isEqualTo(LearningOrganizationStatus.ARCHIVED);
                assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
                assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
            });
        }
    }

    @Test
    void rejectsNonexistentTopicAndScopesOwnershipThroughFullHierarchy() {
        try (var context = startApplicationWithFlyway()) {
            var entityManager = context.getBean(EntityManager.class);
            var subtopics = context.getBean(SubtopicRepository.class);
            var missingTopic = entityManager.getReference(TopicEntity.class, UUID.randomUUID());
            assertThatThrownBy(() -> subtopics.saveAndFlush(new SubtopicEntity(
                    missingTopic, "Invalid", LearningOrganizationStatus.ACTIVE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subtopic-owner");
            var subject = context.getBean(SubjectRepository.class).saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var topic = context.getBean(TopicRepository.class).saveAndFlush(new TopicEntity(
                    subject, "Thorax", LearningOrganizationStatus.ACTIVE));
            var subtopics = context.getBean(SubtopicRepository.class);
            var subtopic = subtopics.saveAndFlush(new SubtopicEntity(
                    topic, "Heart", LearningOrganizationStatus.ACTIVE));
            assertThat(subtopics.findByIdAndTopicSubjectUserId(subtopic.getId(), users.userA().userId())).isPresent();
            assertThat(subtopics.findByIdAndTopicSubjectUserId(subtopic.getId(), users.userB().userId())).isEmpty();
        }
    }

    @Test
    void leafDeleteWorksButDeletingTopicWithSubtopicIsRestricted() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subtopic-delete");
            var subject = context.getBean(SubjectRepository.class).saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var topics = context.getBean(TopicRepository.class);
            var topic = topics.saveAndFlush(new TopicEntity(subject, "Thorax", LearningOrganizationStatus.ACTIVE));
            var subtopics = context.getBean(SubtopicRepository.class);
            var removable = subtopics.saveAndFlush(new SubtopicEntity(
                    topic, "Removable", LearningOrganizationStatus.ACTIVE));
            subtopics.deleteById(removable.getId());
            subtopics.flush();
            assertThat(subtopics.existsById(removable.getId())).isFalse();

            subtopics.saveAndFlush(new SubtopicEntity(topic, "Retained", LearningOrganizationStatus.ACTIVE));
            assertThatThrownBy(() -> {
                topics.deleteById(topic.getId());
                topics.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
