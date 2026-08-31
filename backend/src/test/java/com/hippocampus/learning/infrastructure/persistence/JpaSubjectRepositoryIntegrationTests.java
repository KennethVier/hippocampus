package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.learning.domain.Subject;
import com.hippocampus.learning.domain.SubjectStatus;
import com.hippocampus.learning.port.DuplicateSubjectNameException;
import com.hippocampus.learning.port.SubjectPageRequest;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class JpaSubjectRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void scopesActiveOrderedPagesToOwnerAndPersistsArchivedUpdates() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-adapter");
            var repository = context.getBean(JpaSubjectRepository.class);
            Subject nullOrder = repository.save(Subject.create(users.userA().userId(), "beta", null, null));
            Subject second = repository.save(Subject.create(users.userA().userId(), "Alpha", null, 2));
            repository.save(Subject.create(users.userA().userId(), "aardvark", null, 2));
            repository.save(Subject.create(users.userB().userId(), "Foreign marker", null, 1));
            repository.save(second.archive());

            var page = repository.findActiveByOwner(users.userA().userId(), new SubjectPageRequest(0, 10));
            assertThat(page.items()).extracting(Subject::name).containsExactly("aardvark", "beta");
            assertThat(repository.findOwnedById(nullOrder.id(), users.userB().userId())).isEmpty();

            Subject archivedUpdate = repository.save(second.archive().changeDetails("Archived renamed", "kept", 7));
            assertThat(archivedUpdate.status()).isEqualTo(SubjectStatus.ARCHIVED);
            assertThat(archivedUpdate.ownerId()).isEqualTo(users.userA().userId());
            assertThat(archivedUpdate.name()).isEqualTo("Archived renamed");
        }
    }

    @Test
    void translatesOnlyKnownSubjectNameConstraintAndAllowsNameForAnotherOwner() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "subject-conflict");
            var repository = context.getBean(JpaSubjectRepository.class);
            repository.save(Subject.create(users.userA().userId(), "Anatomy", null, null));

            assertThatThrownBy(() -> repository.save(
                    Subject.create(users.userA().userId(), "anatomy", null, null)))
                    .isInstanceOf(DuplicateSubjectNameException.class);
            assertThat(repository.save(Subject.create(users.userB().userId(), "anatomy", null, null)).id())
                    .isNotNull();

            assertThatThrownBy(() -> repository.save(
                    Subject.create(java.util.UUID.randomUUID(), "Unknown owner", null, null)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .isNotInstanceOf(DuplicateSubjectNameException.class);
        }
    }
}
