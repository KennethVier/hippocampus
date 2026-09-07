package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.materials.domain.VisualAssetDraft;
import com.hippocampus.materials.domain.VisualInterpretationStatus;
import com.hippocampus.materials.domain.VisualType;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.VisualAssetPersistenceException;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class VisualAssetPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void persistsExactMetadataIdempotentlyAndRejectsConflictingReplay() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            Version version = insertPdf(jdbc, 3);
            JdbcVisualAssetPersistence persistence = new JdbcVisualAssetPersistence(jdbc, 10);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            VisualAssetDraft expected = visual(version.versionId(), version.rootId(), 2, "a".repeat(64), 20, 30);

            assertThatThrownBy(() -> persistence.persistOrVerify(version.versionId(), List.of(expected)))
                    .isInstanceOf(IllegalStateException.class);
            inTransaction(transactions, () -> persistence.persistOrVerify(version.versionId(), List.of(expected)));
            UUID firstId = jdbc.sql("SELECT id FROM visual_assets WHERE material_version_id = ?")
                    .param(version.versionId()).query(UUID.class).single();
            inTransaction(transactions, () -> persistence.persistOrVerify(version.versionId(), List.of(expected)));

            assertThat(jdbc.sql("SELECT id FROM visual_assets WHERE material_version_id = ?")
                    .param(version.versionId()).query(UUID.class).single()).isEqualTo(firstId);
            assertThat(jdbc.sql("SELECT count(*) FROM visual_assets WHERE material_version_id = ?")
                    .param(version.versionId()).query(Integer.class).single()).isEqualTo(1);
            VisualAssetDraft conflict = visual(version.versionId(), version.rootId(), 2, "a".repeat(64), 21, 30);
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.persistOrVerify(version.versionId(), List.of(conflict))))
                    .isInstanceOf(VisualAssetPersistenceException.class)
                    .hasMessageContaining("conflicts");
            assertThat(jdbc.sql("SELECT width_px FROM visual_assets WHERE id = ?")
                    .param(firstId).query(Integer.class).single()).isEqualTo(20);
        }
    }

    @Test
    void rejectsCrossMaterialVersionNodeAndAcceptsZeroVisualResult() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            Version first = insertPdf(jdbc, 1);
            Version second = insertPdf(jdbc, 1);
            JdbcVisualAssetPersistence persistence = new JdbcVisualAssetPersistence(jdbc, 10);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);

            inTransaction(transactions, () -> persistence.persistOrVerify(first.versionId(), List.of()));
            assertThat(jdbc.sql("SELECT count(*) FROM visual_assets WHERE material_version_id = ?")
                    .param(first.versionId()).query(Integer.class).single()).isZero();

            VisualAssetDraft foreignNode = visual(
                    first.versionId(), second.rootId(), 1, "b".repeat(64), 1, 1);
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.persistOrVerify(first.versionId(), List.of(foreignNode))))
                    .isInstanceOf(VisualAssetPersistenceException.class);
            assertThat(jdbc.sql("SELECT count(*) FROM visual_assets")
                    .query(Integer.class).single()).isZero();
        }
    }

    private static VisualAssetDraft visual(
            UUID versionId, UUID nodeId, int page, String hash, int width, int height) {
        return new VisualAssetDraft(
                versionId, nodeId, page,
                new BinaryObjectKey("materials/" + versionId + "/visuals/" + page + "/" + hash + ".png"),
                VisualType.OTHER, null, null, VisualInterpretationStatus.UNASSESSED, width, height, hash);
    }

    private static Version insertPdf(JdbcClient jdbc, int pages) {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)")
                .params(userId, userId + "@example.test", now, now).update();
        jdbc.sql("""
                INSERT INTO materials
                    (id, user_id, title, material_type, mime_type, status, created_at, updated_at)
                VALUES (?, ?, 'PDF', 'PDF', 'application/pdf', 'PROCESSING', ?, ?)
                """).params(materialId, userId, now, now).update();
        jdbc.sql("""
                INSERT INTO material_versions
                    (id, material_id, version_number, storage_key, file_size_bytes, page_count,
                     processing_status, created_at)
                VALUES (?, ?, 1, 'objects/pdf', 42, ?, 'PROCESSING', ?)
                """).params(versionId, materialId, pages, now).update();
        jdbc.sql("""
                INSERT INTO document_nodes
                    (id, material_version_id, parent_id, node_type, title, ordinal,
                     start_page, end_page, detection_origin, detection_confidence, created_at)
                VALUES (?, ?, NULL, 'DOCUMENT', NULL, NULL, 1, ?, 'NATIVE', NULL, ?)
                """).params(rootId, versionId, pages, now).update();
        return new Version(versionId, rootId);
    }

    private static void inTransaction(PlatformTransactionManager manager, Runnable operation) {
        new TransactionTemplate(manager).executeWithoutResult(status -> operation.run());
    }

    private static org.springframework.context.ConfigurableApplicationContext startApplication() {
        String trustedExecutable = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
                .toAbsolutePath().normalize().toString();
        return startApplicationWithFlywayAndArguments(
                new Class<?>[0],
                "--hippocampus.materials.processing.pdf.ocr-executable=" + trustedExecutable);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    private record Version(UUID versionId, UUID rootId) {}
}
