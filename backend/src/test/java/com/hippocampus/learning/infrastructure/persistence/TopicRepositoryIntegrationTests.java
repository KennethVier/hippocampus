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

class TopicRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException { resetPostgresSchema(); }

    @Test
    void createsReadsUpdatesAndPersistsBothLifecycleStates() {
        try (var context = startApplicationWithFlyway()) {
            var subjects = context.getBean(SubjectRepository.class);
            var topics = context.getBean(TopicRepository.class);
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "topic-crud");
            var subject = subjects.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var topic = topics.saveAndFlush(new TopicEntity(
                    subject, "Thorax", LearningOrganizationStatus.ACTIVE));

            assertThat(topic.getId()).isNotNull();
            assertThat(topic.getSubject().getId()).isEqualTo(subject.getId());
            assertThat(topic.getCreatedAt()).isNotNull();
            assertThat(topic.getUpdatedAt()).isNotNull();

            var id = topic.getId();
            var createdAt = topic.getCreatedAt();
            var previousUpdatedAt = topic.getUpdatedAt();
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var entityManager = context.getBean(EntityManager.class);
            transactions.executeWithoutResult(ignored -> {
                var managed = topics.findById(id).orElseThrow();
                managed.setName("Cardiothoracic Anatomy");
                managed.setDescription("Thoracic structures");
                managed.setStatus(LearningOrganizationStatus.ARCHIVED);
                topics.flush();
                entityManager.clear();
                var reloaded = topics.findById(id).orElseThrow();
                assertThat(reloaded.getName()).isEqualTo("Cardiothoracic Anatomy");
                assertThat(reloaded.getDescription()).isEqualTo("Thoracic structures");
                assertThat(reloaded.getStatus()).isEqualTo(LearningOrganizationStatus.ARCHIVED);
                assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
                assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
            });
        }
    }

    @Test
    void rejectsNonexistentSubjectAndScopesOwnershipThroughSubject() {
        try (var context = startApplicationWithFlyway()) {
            var entityManager = context.getBean(EntityManager.class);
            var topics = context.getBean(TopicRepository.class);
            var missingSubject = entityManager.getReference(SubjectEntity.class, UUID.randomUUID());
            assertThatThrownBy(() -> topics.saveAndFlush(new TopicEntity(
                    missingSubject, "Invalid", LearningOrganizationStatus.ACTIVE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "topic-owner");
            var subject = context.getBean(SubjectRepository.class).saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var topics = context.getBean(TopicRepository.class);
            var topic = topics.saveAndFlush(new TopicEntity(subject, "Thorax", LearningOrganizationStatus.ACTIVE));
            assertThat(topics.findByIdAndSubjectUserId(topic.getId(), users.userA().userId())).isPresent();
            assertThat(topics.findByIdAndSubjectUserId(topic.getId(), users.userB().userId())).isEmpty();
        }
    }

    @Test
    void childlessDeleteWorksButDeletingSubjectWithTopicIsRestricted() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "topic-delete");
            var subjects = context.getBean(SubjectRepository.class);
            var topics = context.getBean(TopicRepository.class);
            var subject = subjects.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            var removable = topics.saveAndFlush(new TopicEntity(
                    subject, "Removable", LearningOrganizationStatus.ACTIVE));
            topics.deleteById(removable.getId());
            topics.flush();
            assertThat(topics.existsById(removable.getId())).isFalse();

            topics.saveAndFlush(new TopicEntity(subject, "Retained", LearningOrganizationStatus.ACTIVE));
            assertThatThrownBy(() -> {
                subjects.deleteById(subject.getId());
                subjects.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
