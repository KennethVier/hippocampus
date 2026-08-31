package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

import jakarta.persistence.EntityManager;

class MaterialVersionRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void savesMultipleVersionsAndUpdatesDiscoveredProcessingMetadata() {
        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "versions");
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            var first = new MaterialVersionEntity(material.getId(), 1, "PROCESSING");
            first.setStorageKey("private/materials/v1");
            first.setFileSizeBytes(4096L);
            first = versions.saveAndFlush(first);
            MaterialVersionEntity second = versions.saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 2, "UPLOADED"));

            first.setPageCount(12);
            first.setContentHash("sha256:source-one");
            first.setProcessingStatus("READY");
            first.setProcessingProgress(new BigDecimal("100.00"));
            first.setExtractionMethod("PDF_TEXT");
            first.setExtractionQuality("HIGH");
            first.setActivatedAt(Instant.parse("2026-08-31T12:00:00Z"));
            versions.saveAndFlush(first);
            context.getBean(EntityManager.class).clear();

            MaterialVersionEntity reloaded = versions.findById(first.getId()).orElseThrow();
            assertThat(reloaded.getMaterialId()).isEqualTo(material.getId());
            assertThat(reloaded.getVersionNumber()).isEqualTo(1);
            assertThat(reloaded.getStorageKey()).isEqualTo("private/materials/v1");
            assertThat(reloaded.getFileSizeBytes()).isEqualTo(4096L);
            assertThat(reloaded.getPageCount()).isEqualTo(12);
            assertThat(reloaded.getContentHash()).isEqualTo("sha256:source-one");
            assertThat(reloaded.getProcessingStatus()).isEqualTo("READY");
            assertThat(reloaded.getProcessingProgress()).isEqualByComparingTo("100.00");
            assertThat(reloaded.getExtractionMethod()).isEqualTo("PDF_TEXT");
            assertThat(reloaded.getExtractionQuality()).isEqualTo("HIGH");
            assertThat(reloaded.getActivatedAt()).isEqualTo(Instant.parse("2026-08-31T12:00:00Z"));
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(second.getVersionNumber()).isEqualTo(2);
        }
    }

    @Test
    void permitsNullOptionalMetadataAndPreservesRepresentativeProcessingStatus() {
        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "optional");
            MaterialVersionEntity version = context.getBean(SpringDataMaterialVersionRepository.class).saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 1, "PARTIALLY_READY"));

            assertThat(version.getProcessingStatus()).isEqualTo("PARTIALLY_READY");
            assertThat(version.getStorageKey()).isNull();
            assertThat(version.getFileSizeBytes()).isNull();
            assertThat(version.getPageCount()).isNull();
            assertThat(version.getContentHash()).isNull();
            assertThat(version.getProcessingProgress()).isNull();
            assertThat(version.getExtractionMethod()).isNull();
            assertThat(version.getExtractionQuality()).isNull();
            assertThat(version.getActivatedAt()).isNull();
        }
    }

    @Test
    void rejectsMissingParentAndRestrictsDeletingParentWithVersion() {
        try (var context = startApplicationWithFlyway()) {
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            assertThatThrownBy(() -> versions.saveAndFlush(
                    new MaterialVersionEntity(UUID.randomUUID(), 1, "UPLOADED")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "parent-delete");
            context.getBean(SpringDataMaterialVersionRepository.class).saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 1, "UPLOADED"));
            var materials = context.getBean(SpringDataMaterialRepository.class);
            assertThatThrownBy(() -> {
                materials.deleteById(material.getId());
                materials.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void enforcesVersionUniquenessPerMaterial() {
        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "duplicate-version");
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            versions.saveAndFlush(new MaterialVersionEntity(material.getId(), 1, "UPLOADED"));
            assertThatThrownBy(() -> versions.saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 1, "PROCESSING")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "shared-version-number");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            MaterialEntity first = materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "First", "PDF", "UPLOADED"));
            MaterialEntity second = materials.saveAndFlush(
                    new MaterialEntity(users.userB().userId(), "Second", "PDF", "UPLOADED"));
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            assertThat(versions.saveAndFlush(new MaterialVersionEntity(first.getId(), 1, "UPLOADED")).getId())
                    .isNotNull();
            assertThat(versions.saveAndFlush(new MaterialVersionEntity(second.getId(), 1, "UPLOADED")).getId())
                    .isNotNull();
        }
    }

    @Test
    void rejectsInvalidIntrinsicNumericMetadata() {
        assertInvalidVersion(new MaterialVersionEntity(UUID.randomUUID(), 0, "UPLOADED"), "invalid-version-zero", true);
        assertInvalidVersion(new MaterialVersionEntity(UUID.randomUUID(), -1, "UPLOADED"), "invalid-version-negative", true);

        var negativeSize = new MaterialVersionEntity(UUID.randomUUID(), 1, "UPLOADED");
        negativeSize.setFileSizeBytes(-1L);
        assertInvalidVersion(negativeSize, "invalid-size", false);

        var negativePages = new MaterialVersionEntity(UUID.randomUUID(), 1, "UPLOADED");
        negativePages.setPageCount(-1);
        assertInvalidVersion(negativePages, "invalid-pages", false);
    }

    @Test
    void enforcesSameMaterialActiveVersionAndRestrictiveDeletion() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "active-own");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            MaterialEntity material = materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Active", "PDF", "PROCESSING"));
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            MaterialVersionEntity version = versions.saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 1, "READY"));
            material.setActiveVersionId(version.getId());
            materials.saveAndFlush(material);
            assertThat(materials.findById(material.getId()).orElseThrow().getActiveVersionId()).isEqualTo(version.getId());

            assertThatThrownBy(() -> {
                versions.deleteById(version.getId());
                versions.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "clear-active");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            var versions = context.getBean(SpringDataMaterialVersionRepository.class);
            MaterialVersionEntity version = versions.saveAndFlush(
                    new MaterialVersionEntity(material.getId(), 1, "READY"));
            material.setActiveVersionId(version.getId());
            materials.saveAndFlush(material);
            material.setActiveVersionId(null);
            materials.saveAndFlush(material);
            versions.deleteById(version.getId());
            versions.flush();
            assertThat(versions.existsById(version.getId())).isFalse();
        }
    }

    @Test
    void rejectsCrossMaterialAndNonexistentActiveVersion() {
        try (var context = startApplicationWithFlyway()) {
            var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "cross-active");
            var materials = context.getBean(SpringDataMaterialRepository.class);
            MaterialEntity first = materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "First", "PDF", "PROCESSING"));
            MaterialEntity second = materials.saveAndFlush(
                    new MaterialEntity(users.userA().userId(), "Second", "PDF", "PROCESSING"));
            MaterialVersionEntity secondVersion = context.getBean(SpringDataMaterialVersionRepository.class)
                    .saveAndFlush(new MaterialVersionEntity(second.getId(), 1, "READY"));
            first.setActiveVersionId(secondVersion.getId());
            assertThatThrownBy(() -> materials.saveAndFlush(first))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, "missing-active");
            material.setActiveVersionId(UUID.randomUUID());
            assertThatThrownBy(() -> context.getBean(SpringDataMaterialRepository.class).saveAndFlush(material))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private static MaterialEntity persistMaterial(
            org.springframework.context.ApplicationContext context, String fixtureName) {
        var users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), fixtureName);
        return context.getBean(SpringDataMaterialRepository.class).saveAndFlush(
                new MaterialEntity(users.userA().userId(), "Material", "PDF", "UPLOADED"));
    }

    private static void assertInvalidVersion(
            MaterialVersionEntity candidate, String fixtureName, boolean replaceParent) {
        try (var context = startApplicationWithFlyway()) {
            MaterialEntity material = persistMaterial(context, fixtureName);
            MaterialVersionEntity actual = candidate;
            if (replaceParent) {
                actual = new MaterialVersionEntity(material.getId(), candidate.getVersionNumber(), "UPLOADED");
            } else if (candidate.getFileSizeBytes() != null) {
                actual = new MaterialVersionEntity(material.getId(), 1, "UPLOADED");
                actual.setFileSizeBytes(candidate.getFileSizeBytes());
            } else {
                actual = new MaterialVersionEntity(material.getId(), 1, "UPLOADED");
                actual.setPageCount(candidate.getPageCount());
            }
            MaterialVersionEntity invalid = actual;
            assertThatThrownBy(() -> context.getBean(SpringDataMaterialVersionRepository.class).saveAndFlush(invalid))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
