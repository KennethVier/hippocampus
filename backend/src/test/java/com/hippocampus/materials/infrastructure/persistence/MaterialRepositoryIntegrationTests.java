package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
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
}
