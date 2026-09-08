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

import com.hippocampus.materials.domain.VisualContextAssociation;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class VisualContextPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void fillsContextIdempotentlyWithoutChangingExtractionMetadataOrCreatingAssets() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            Version version = insertPdf(jdbc);
            UUID visualId = insertVisual(jdbc, version);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            JdbcVisualContextRepository repository = new JdbcVisualContextRepository(jdbc);
            VisualContextAssociation detected = association(
                    visualId, version, "Figure 4. Cardiac conduction", "Local explanation.");

            assertThatThrownBy(() -> repository.persist(version.versionId(), List.of(detected)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("transaction");
            assertThat(repository.findByMaterialVersion(version.versionId())).hasSize(1);
            inTransaction(transactions, () -> repository.persist(version.versionId(), List.of(detected)));
            inTransaction(transactions, () -> repository.persist(version.versionId(), List.of(detected)));

            assertThat(jdbc.sql("""
                    SELECT caption, nearby_text, visual_type, interpretation_status, storage_key,
                           content_hash, page_number, document_node_id, width_px, height_px
                    FROM visual_assets WHERE id = ?
                    """).param(visualId).query((result, row) -> List.of(
                            result.getString("caption"), result.getString("nearby_text"),
                            result.getString("visual_type"), result.getString("interpretation_status"),
                            result.getString("storage_key"), result.getString("content_hash"),
                            result.getInt("page_number"), result.getObject("document_node_id", UUID.class),
                            result.getInt("width_px"), result.getInt("height_px"))).single())
                    .containsExactly(
                            "Figure 4. Cardiac conduction", "Local explanation.", "OTHER", "UNASSESSED",
                            "materials/visual.png", "a".repeat(64), 1, version.rootId(), 20, 30);
            assertThat(jdbc.sql("SELECT count(*) FROM visual_assets WHERE material_version_id = ?")
                    .param(version.versionId()).query(Integer.class).single()).isEqualTo(1);
        }
    }

    @Test
    void preservesExistingEnrichmentAndRejectsInconsistentAssociationProvenance() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            Version version = insertPdf(jdbc);
            UUID visualId = insertVisual(jdbc, version);
            jdbc.sql("UPDATE visual_assets SET caption = 'Manual caption', nearby_text = 'Manual context' WHERE id = ?")
                    .param(visualId).update();
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            JdbcVisualContextRepository repository = new JdbcVisualContextRepository(jdbc);

            inTransaction(transactions, () -> repository.persist(version.versionId(), List.of(association(
                    visualId, version, "Figure 4. Detected", "Detected context"))));

            assertThat(jdbc.sql("SELECT caption, nearby_text FROM visual_assets WHERE id = ?")
                    .param(visualId).query((result, row) -> List.of(
                            result.getString("caption"), result.getString("nearby_text"))).single())
                    .containsExactly("Manual caption", "Manual context");

            VisualContextAssociation wrongPage = new VisualContextAssociation(
                    visualId, version.versionId(), version.rootId(), 2, "Figure 4", null);
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> repository.persist(version.versionId(), List.of(wrongPage))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("provenance");
        }
    }

    private static VisualContextAssociation association(
            UUID visualId, Version version, String caption, String nearbyText) {
        return new VisualContextAssociation(
                visualId, version.versionId(), version.rootId(), 1, caption, nearbyText);
    }

    private static UUID insertVisual(JdbcClient jdbc, Version version) {
        UUID visualId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO visual_assets
                    (id, material_version_id, document_node_id, page_number, storage_key, visual_type,
                     caption, nearby_text, interpretation_status, width_px, height_px, content_hash, created_at)
                VALUES (?, ?, ?, 1, 'materials/visual.png', 'OTHER', NULL, NULL, 'UNASSESSED',
                        20, 30, ?, ?)
                """).params(
                        visualId, version.versionId(), version.rootId(), "a".repeat(64),
                        OffsetDateTime.now(ZoneOffset.UTC)).update();
        return visualId;
    }

    private static Version insertPdf(JdbcClient jdbc) {
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
                VALUES (?, ?, 1, 'objects/pdf', 42, 1, 'PROCESSING', ?)
                """).params(versionId, materialId, now).update();
        jdbc.sql("""
                INSERT INTO document_nodes
                    (id, material_version_id, parent_id, node_type, title, ordinal,
                     start_page, end_page, detection_origin, detection_confidence, created_at)
                VALUES (?, ?, NULL, 'DOCUMENT', NULL, NULL, 1, 1, 'NATIVE', NULL, ?)
                """).params(rootId, versionId, now).update();
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
