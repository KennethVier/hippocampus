package com.hippocampus.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

import jakarta.persistence.EntityManager;

class UserRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @Test
    void createsReadsAndUpdatesUserWithGeneratedIdentityAndTimestamps() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(UserRepository.class);
            var entityManager = context.getBean(EntityManager.class);
            var transactions = new TransactionTemplate(
                    context.getBean(PlatformTransactionManager.class));
            var user = repository.saveAndFlush(
                    new UserEntity("student@example.test", "Student", UserStatus.ACTIVE));

            assertThat(user.getId()).isNotNull();
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();

            var id = user.getId();
            var createdAt = user.getCreatedAt();
            var previousUpdatedAt = user.getUpdatedAt();
            transactions.executeWithoutResult(ignored -> {
                var managed = repository.findById(id).orElseThrow();
                managed.setDisplayName("Updated Student");
                managed.setStatus(UserStatus.DISABLED);
                repository.flush();
                entityManager.clear();

                var reloaded = repository.findById(id).orElseThrow();
                assertThat(reloaded.getEmail()).isEqualTo("student@example.test");
                assertThat(reloaded.getDisplayName()).isEqualTo("Updated Student");
                assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);
                assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
                assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
            });
        }
    }

    @Test
    void persistsNullDisplayNameAndAllCurrentStatusesAsStrings() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(UserRepository.class);
            for (var status : UserStatus.values()) {
                repository.save(new UserEntity(status.name().toLowerCase() + "@example.test", null, status));
            }
            repository.flush();

            assertThat(repository.findAll())
                    .allSatisfy(user -> assertThat(user.getDisplayName()).isNull())
                    .extracting(UserEntity::getStatus)
                    .containsExactlyInAnyOrder(UserStatus.values());

            try (var connection = openPostgresConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT status FROM users ORDER BY status")) {
                var statuses = new java.util.ArrayList<String>();
                while (result.next()) {
                    statuses.add(result.getString("status"));
                }
                assertThat(statuses).containsExactly("ACTIVE", "DELETED", "DISABLED");
            }
        }
    }

    @Test
    void physicallyDeletesStandaloneUserAsRepositoryPrimitive() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(UserRepository.class);
            var user = repository.saveAndFlush(
                    new UserEntity("delete@example.test", "Delete", UserStatus.ACTIVE));

            repository.deleteById(user.getId());
            repository.flush();

            assertThat(repository.existsById(user.getId())).isFalse();
        }
    }

    @Test
    void rejectsDuplicateEmailWhenInsertIsFlushed() {
        try (var context = startApplicationWithFlyway()) {
            var repository = context.getBean(UserRepository.class);
            repository.saveAndFlush(
                    new UserEntity("duplicate@example.test", "First", UserStatus.ACTIVE));

            assertThatThrownBy(() -> repository.saveAndFlush(
                    new UserEntity("duplicate@example.test", "Second", UserStatus.ACTIVE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
