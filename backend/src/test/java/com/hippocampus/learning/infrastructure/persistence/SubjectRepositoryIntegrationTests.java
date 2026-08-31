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

class SubjectRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void createsReadsAndUpdatesSubjectWithGeneratedIdentityAndTimestamps() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(SpringDataSubjectRepository.class);
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-crud");
            var subject = new SubjectEntity(users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE);
            subject.setDescription(null);
            subject.setSortOrder(null);
            subject = repository.saveAndFlush(subject);

            assertThat(subject.getId()).isNotNull();
            assertThat(subject.getUserId()).isEqualTo(users.userA().userId());
            assertThat(subject.getDescription()).isNull();
            assertThat(subject.getSortOrder()).isNull();
            assertThat(subject.getCreatedAt()).isNotNull();
            assertThat(subject.getUpdatedAt()).isNotNull();

            var id = subject.getId();
            var createdAt = subject.getCreatedAt();
            var previousUpdatedAt = subject.getUpdatedAt();
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var entityManager = context.getBean(EntityManager.class);
            transactions.executeWithoutResult(ignored -> {
                var managed = repository.findById(id).orElseThrow();
                managed.setName("Gross Anatomy");
                managed.setDescription("Structural medicine");
                managed.setSortOrder(2);
                managed.setStatus(LearningOrganizationStatus.ARCHIVED);
                repository.flush();
                entityManager.clear();

                var reloaded = repository.findById(id).orElseThrow();
                assertThat(reloaded.getName()).isEqualTo("Gross Anatomy");
                assertThat(reloaded.getDescription()).isEqualTo("Structural medicine");
                assertThat(reloaded.getSortOrder()).isEqualTo(2);
                assertThat(reloaded.getStatus()).isEqualTo(LearningOrganizationStatus.ARCHIVED);
                assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
                assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
            });
        }
    }

    @Test
    void physicallyDeletesChildlessSubjectAsRepositoryCrudPrimitive() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(SpringDataSubjectRepository.class);
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-delete");
            var subject = repository.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Delete primitive", LearningOrganizationStatus.ACTIVE));

            repository.deleteById(subject.getId());
            repository.flush();

            assertThat(repository.existsById(subject.getId())).isFalse();
        }
    }

    @Test
    void rejectsSubjectForNonexistentUser() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(SpringDataSubjectRepository.class);
            assertThatThrownBy(() -> repository.saveAndFlush(new SubjectEntity(
                    UUID.randomUUID(), "Anatomy", LearningOrganizationStatus.ACTIVE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void enforcesCaseInsensitiveNameUniquenessPerUser() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(SpringDataSubjectRepository.class);
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-unique");
            repository.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));

            assertThatThrownBy(() -> repository.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "anatomy", LearningOrganizationStatus.ACTIVE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void allowsSameSubjectNameForDifferentUsersAndScopesOwnerLookup() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(SpringDataSubjectRepository.class);
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-owner");
            var subjectA = repository.saveAndFlush(new SubjectEntity(
                    users.userA().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));
            repository.saveAndFlush(new SubjectEntity(
                    users.userB().userId(), "Anatomy", LearningOrganizationStatus.ACTIVE));

            assertThat(repository.findByIdAndUserId(subjectA.getId(), users.userA().userId())).isPresent();
            assertThat(repository.findByIdAndUserId(subjectA.getId(), users.userB().userId())).isEmpty();
        }
    }
}
