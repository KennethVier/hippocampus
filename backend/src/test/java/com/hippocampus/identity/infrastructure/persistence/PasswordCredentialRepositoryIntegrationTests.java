package com.hippocampus.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class PasswordCredentialRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach void resetDatabase() throws Exception { resetPostgresSchema(); }

    @Test
    void hibernateValidatesV3AndCredentialPersistsUpdatesWithTimestamps() {
        try (var context = startApplicationWithFlyway()) {
            var users = context.getBean(UserRepository.class);
            var credentials = context.getBean(PasswordCredentialRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            var user = users.saveAndFlush(new UserEntity("credential@example.test", "Student", UserStatus.ACTIVE));
            var firstHash = encoder.encode("runtime password one");

            var saved = credentials.saveAndFlush(new PasswordCredentialEntity(user.getId(), firstHash));
            assertThat(saved.getUserId()).isEqualTo(user.getId());
            assertThat(saved.getPasswordHash()).isEqualTo(firstHash);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();

            var secondHash = encoder.encode("runtime password two");
            saved.setPasswordHash(secondHash);
            credentials.saveAndFlush(saved);
            assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(secondHash);
        }
    }

    @Test
    void primaryKeyEnforcesOneCredentialPerUserAndPasswordHashIsRequired() {
        try (var context = startApplicationWithFlyway()) {
            var users = context.getBean(UserRepository.class);
            var credentials = context.getBean(PasswordCredentialRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            var user = users.saveAndFlush(new UserEntity("constraints@example.test", "Student", UserStatus.ACTIVE));
            credentials.saveAndFlush(new PasswordCredentialEntity(user.getId(), encoder.encode("runtime one")));

            assertThatThrownBy(() -> insertCredential(user.getId(), encoder.encode("runtime duplicate")))
                    .isInstanceOf(Exception.class);

            var nullHashUser = users.saveAndFlush(
                    new UserEntity("null-hash@example.test", "Student", UserStatus.ACTIVE));
            assertThatThrownBy(() -> credentials.saveAndFlush(
                    new PasswordCredentialEntity(nullHashUser.getId(), null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void foreignKeyRejectsUnknownUserAndPhysicalDeletionCascades() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = context.getBean(UserRepository.class);
            var credentials = context.getBean(PasswordCredentialRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            var unknownId = UUID.randomUUID();
            assertThatThrownBy(() -> insertCredential(unknownId, encoder.encode("runtime unknown")))
                    .isInstanceOf(Exception.class);

            var user = users.saveAndFlush(new UserEntity("cascade@example.test", "Student", UserStatus.ACTIVE));
            credentials.saveAndFlush(new PasswordCredentialEntity(user.getId(), encoder.encode("runtime cascade")));
            users.deleteById(user.getId());
            users.flush();
            assertThat(credentials.existsById(user.getId())).isFalse();
        }
    }

    private static void insertCredential(UUID userId, String hash) throws Exception {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO user_password_credentials(user_id, password_hash) VALUES (?, ?)")) {
            statement.setObject(1, userId);
            statement.setString(2, hash);
            statement.executeUpdate();
        }
    }
}
