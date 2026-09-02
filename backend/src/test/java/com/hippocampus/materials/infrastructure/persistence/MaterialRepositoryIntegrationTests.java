package com.hippocampus.materials.infrastructure.persistence;

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
import com.hippocampus.materials.port.MaterialDeletionOutcome;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

import jakarta.persistence.EntityManager;

class MaterialRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void savesAndReloadsMaterialMetadataWithScalarOwnerAndTimestamps() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-crud");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            var material = new MaterialEntity(users.userA().userId(), "Cardiac anatomy", "PDF", "UPLOADED");
            material.setOriginalFilename("heart atlas.pdf");
            material.setMimeType("application/pdf");
            material.setStorageKey("private/materials/source-1");

            material = materials.saveAndFlush(material);
            UUID materialId = material.getId();
            context.getBean(EntityManager.class).clear();

            MaterialEntity reloaded = materials.findById(materialId).orElseThrow();
            assertThat(reloaded.getUserId()).isEqualTo(users.userA().userId());
            assertThat(reloaded.getTitle()).isEqualTo("Cardiac anatomy");
            assertThat(reloaded.getMaterialType()).isEqualTo("PDF");
            assertThat(reloaded.getStatus()).isEqualTo("UPLOADED");
            assertThat(reloaded.getOriginalFilename()).isEqualTo("heart atlas.pdf");
            assertThat(reloaded.getMimeType()).isEqualTo("application/pdf");
            assertThat(reloaded.getStorageKey()).isEqualTo("private/materials/source-1");
            assertThat(reloaded.getActiveVersionId()).isNull();
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(reloaded.getUpdatedAt()).isNotNull();
        }
    }

    @Test
    void acceptsExistingOwnersAndRejectsNonexistentOwner() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-owner");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            assertThat(materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Owned A", "TEXT", "READY")).getId()).isNotNull();
            assertThat(materials.saveAndFlush(
                    new MaterialEntity(users.userB().userId(), "Owned B", "IMAGE", "PROCESSING")).getId()).isNotNull();
        }

        try (var context = startApplicationWithFlyway()) {
            var materials = context.getBean(SpringDataMaterialRepository.class);
            assertThatThrownBy(() -> materials.saveAndFlush(
                    new MaterialEntity(UUID.randomUUID(), "Orphan", "PDF", "UPLOADED")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void rejectsNullRequiredTypeAndStatus() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-null-type");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            assertThatThrownBy(() -> materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Missing type", null, "UPLOADED")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-null-status");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            assertThatThrownBy(() -> materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Missing status", "PDF", null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void restrictsDeletingReferencedOwner() {
        try (var context = startApplicationWithFlyway()) {
            var userRepository = context.getBean(UserRepository.class);
            var users = OwnershipTestUsers.persistWith(userRepository, "material-user-delete");
            context.getBean(SpringDataMaterialRepository.class).saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Retained", "PDF", "UPLOADED"));

            assertThatThrownBy(() -> {
                userRepository.deleteById(users.userA().userId());
                userRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void reportsActualAlreadyDeletedForeignAndMissingOutcomesWithoutChangingForeignState() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-delete-outcome");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            MaterialEntity owned = materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Owned", "PDF", "UPLOADED"));
            MaterialVersionEntity version = versions.saveAndFlush(
                    new MaterialVersionEntity(owned.getId(), 1, "UPLOADED"));
            owned.setActiveVersionId(version.getId());
            owned = materials.saveAndFlush(owned);
            UUID ownedId = owned.getId();
            MaterialEntity foreign = materials.saveAndFlush(
                    new MaterialEntity(users.userB().userId(), "Foreign", "PDF", "UPLOADED"));
            var originalUpdatedAt = owned.getUpdatedAt();

            MaterialRepository repository = context.getBean(MaterialRepository.class);
            TransactionTemplate transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            MaterialDeletionOutcome first = transactions.execute(
                    ignored -> repository.markDeletedOwned(ownedId, users.userA().userId()));
            MaterialEntity deleted = materials.findById(ownedId).orElseThrow();
            var deletedAt = deleted.getUpdatedAt();
            MaterialDeletionOutcome repeated = transactions.execute(
                    ignored -> repository.markDeletedOwned(ownedId, users.userA().userId()));
            MaterialDeletionOutcome foreignOutcome = transactions.execute(
                    ignored -> repository.markDeletedOwned(foreign.getId(), users.userA().userId()));
            MaterialDeletionOutcome missing = transactions.execute(
                    ignored -> repository.markDeletedOwned(UUID.randomUUID(), users.userA().userId()));

            assertThat(first).isEqualTo(MaterialDeletionOutcome.DELETED);
            assertThat(repeated).isEqualTo(MaterialDeletionOutcome.ALREADY_DELETED);
            assertThat(foreignOutcome).isEqualTo(MaterialDeletionOutcome.NOT_FOUND);
            assertThat(missing).isEqualTo(MaterialDeletionOutcome.NOT_FOUND);
            assertThat(deleted.getStatus()).isEqualTo("DELETED");
            assertThat(deleted.getActiveVersionId()).isNull();
            assertThat(deletedAt).isAfter(originalUpdatedAt);
            assertThat(materials.findById(ownedId).orElseThrow().getUpdatedAt()).isEqualTo(deletedAt);
            assertThat(materials.findById(foreign.getId()).orElseThrow().getStatus()).isEqualTo("UPLOADED");
            assertThat(versions.findAll()).extracting(MaterialVersionEntity::getId).containsExactly(version.getId());
        }
    }
}
