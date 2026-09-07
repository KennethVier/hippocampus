package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.DetectedDocumentStructure.Node;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistenceException;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class DetectedDocumentStructurePersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void atomicallyPersistsExactReplayWithoutChangingRootOrPageEvidence() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = extractedPdf(context, jdbc, 3);
            UUID rootId = rootId(jdbc, versionId);
            List<PageIdentity> pageIdentities = pageIdentities(jdbc, versionId);
            JdbcDetectedDocumentStructurePersistence persistence =
                    new JdbcDetectedDocumentStructurePersistence(jdbc, 20);
            DetectedDocumentStructure structure = structure(versionId, rootId, 3);

            assertThatThrownBy(() -> persistence.persistOrVerify(structure))
                    .isInstanceOf(IllegalStateException.class);
            inTransaction(context.getBean(PlatformTransactionManager.class),
                    () -> persistence.persistOrVerify(structure));
            List<NodeIdentity> first = nodeIdentities(jdbc, versionId, rootId);
            inTransaction(context.getBean(PlatformTransactionManager.class),
                    () -> persistence.persistOrVerify(structure));

            assertThat(nodeIdentities(jdbc, versionId, rootId)).isEqualTo(first);
            assertThat(first).extracting(NodeIdentity::type).containsExactly("CHAPTER", "SECTION");
            assertThat(first.get(1).parentId()).isEqualTo(first.getFirst().id());
            assertThat(pageIdentities(jdbc, versionId)).isEqualTo(pageIdentities);
            assertThat(rootId(jdbc, versionId)).isEqualTo(rootId);
        }
    }

    @Test
    void partialDifferentAndLaterOriginStateFailWithoutDeletion() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            UUID partialVersion = extractedPdf(context, jdbc, 3);
            UUID partialRoot = rootId(jdbc, partialVersion);
            insertExisting(jdbc, partialVersion, partialRoot, 1, "CHAPTER", "Partial", "HEURISTIC");
            JdbcDetectedDocumentStructurePersistence persistence =
                    new JdbcDetectedDocumentStructurePersistence(jdbc, 20);

            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.persistOrVerify(structure(partialVersion, partialRoot, 3))))
                    .isInstanceOf(DetectedDocumentStructurePersistenceException.class);
            assertThat(nodeCount(jdbc, partialVersion, partialRoot)).isEqualTo(1);

            UUID laterVersion = extractedPdf(context, jdbc, 3);
            UUID laterRoot = rootId(jdbc, laterVersion);
            insertExisting(jdbc, laterVersion, laterRoot, 1, "SECTION", "AI node", "AI_ASSISTED");
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.persistOrVerify(structure(laterVersion, laterRoot, 3))))
                    .isInstanceOf(DetectedDocumentStructurePersistenceException.class);
            assertThat(nodeCount(jdbc, laterVersion, laterRoot)).isEqualTo(1);

            UUID differentVersion = extractedPdf(context, jdbc, 3);
            UUID differentRoot = rootId(jdbc, differentVersion);
            UUID differentChapter = insertExisting(
                    jdbc, differentVersion, differentRoot, 1, "CHAPTER", "Different", "HEURISTIC");
            insertExisting(jdbc, differentVersion, differentChapter, 1, "SECTION", "Different child", "HEURISTIC");
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.persistOrVerify(structure(differentVersion, differentRoot, 3))))
                    .isInstanceOf(DetectedDocumentStructurePersistenceException.class);
            assertThat(nodeCount(jdbc, differentVersion, differentRoot)).isEqualTo(2);
        }
    }

    @Test
    void insertionFailureRollsBackWholeHierarchy() {
        try (var context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = extractedPdf(context, jdbc, 2);
            UUID rootId = rootId(jdbc, versionId);
            DetectedDocumentStructure invalidSiblingOrder = new DetectedDocumentStructure(
                    versionId, rootId, 2, List.of(
                            new Node(null, DocumentNodeType.CHAPTER, "1 First", 1, 1, 1,
                                    DocumentNodeDetectionOrigin.HEURISTIC, "HIGH"),
                            new Node(null, DocumentNodeType.CHAPTER, "2 Second", 1, 2, 2,
                                    DocumentNodeDetectionOrigin.HEURISTIC, "HIGH")));
            JdbcDetectedDocumentStructurePersistence persistence =
                    new JdbcDetectedDocumentStructurePersistence(jdbc, 20);

            assertThatThrownBy(() -> inTransaction(context.getBean(PlatformTransactionManager.class),
                    () -> persistence.persistOrVerify(invalidSiblingOrder)))
                    .isInstanceOf(DetectedDocumentStructurePersistenceException.class);
            assertThat(nodeCount(jdbc, versionId, rootId)).isZero();
        }
    }

    @Test
    void concurrentLifecycleDeletionCannotBecomeStaleStructureWrite() throws Exception {
        try (var context = startApplication();
                Connection deleting = openPostgresConnection()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            UUID versionId = extractedPdf(context, jdbc, 3);
            UUID rootId = rootId(jdbc, versionId);
            UUID materialId = jdbc.sql("SELECT material_id FROM material_versions WHERE id = ?")
                    .param(versionId).query(UUID.class).single();
            JdbcDetectedDocumentStructurePersistence persistence =
                    new JdbcDetectedDocumentStructurePersistence(jdbc, 20);

            deleting.setAutoCommit(false);
            try (PreparedStatement statement = deleting.prepareStatement(
                    "UPDATE materials SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                statement.setObject(1, materialId);
                statement.executeUpdate();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            boolean committed = false;
            try {
                Future<Void> write = executor.submit(() -> {
                    inTransaction(transactions, () -> persistence.persistOrVerify(structure(versionId, rootId, 3)));
                    return null;
                });

                assertThatThrownBy(() -> write.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                deleting.commit();
                committed = true;

                assertThatThrownBy(() -> get(write))
                        .isInstanceOf(DetectedDocumentStructurePersistenceException.class);
                assertThat(nodeCount(jdbc, versionId, rootId)).isZero();
            } finally {
                if (!committed) {
                    deleting.rollback();
                }
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static DetectedDocumentStructure structure(UUID versionId, UUID rootId, int pages) {
        return new DetectedDocumentStructure(versionId, rootId, pages, List.of(
                new Node(null, DocumentNodeType.CHAPTER, "1 Foundations", 1, 1, pages,
                        DocumentNodeDetectionOrigin.HEURISTIC, "HIGH"),
                new Node(0, DocumentNodeType.SECTION, "1.1 Cells", 1, 2, pages,
                        DocumentNodeDetectionOrigin.HEURISTIC, "MEDIUM")));
    }

    private static ConfigurableApplicationContext startApplication() {
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

    private static UUID extractedPdf(
            org.springframework.context.ConfigurableApplicationContext context, JdbcClient jdbc, int pages) {
        UUID versionId = insertPdf(jdbc);
        PersistPdfPageBatch batches = context.getBean(PersistPdfPageBatch.class);
        for (int page = 1; page <= pages; page++) {
            batches.execute(versionId, new PdfPageBatch(page, page, List.of(new PdfExtractedPage(
                    page, "page " + page, TextBlockExtractionMethod.NATIVE, null))));
        }
        context.getBean(FinalizePdfExtraction.class).execute(versionId, pages);
        return versionId;
    }

    private static UUID insertPdf(JdbcClient jdbc) {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
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
                    (id, material_id, version_number, storage_key, file_size_bytes, processing_status, created_at)
                VALUES (?, ?, 1, 'objects/pdf', 42, 'PROCESSING', ?)
                """).params(versionId, materialId, now).update();
        return versionId;
    }

    private static UUID insertExisting(
            JdbcClient jdbc, UUID versionId, UUID parentId, int ordinal,
            String type, String title, String origin) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO document_nodes
                    (id, material_version_id, parent_id, node_type, title, ordinal,
                     start_page, end_page, detection_origin, detection_confidence, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, 1, ?, 'LOW', CURRENT_TIMESTAMP)
                """).params(id, versionId, parentId, type, title, ordinal, origin).update();
        return id;
    }

    private static UUID rootId(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("""
                SELECT id FROM document_nodes
                WHERE material_version_id = ? AND node_type = 'DOCUMENT'
                """).param(versionId).query(UUID.class).single();
    }

    private static int nodeCount(JdbcClient jdbc, UUID versionId, UUID rootId) {
        return jdbc.sql("SELECT count(*) FROM document_nodes WHERE material_version_id = ? AND id <> ?")
                .params(versionId, rootId).query(Integer.class).single();
    }

    private static List<NodeIdentity> nodeIdentities(JdbcClient jdbc, UUID versionId, UUID rootId) {
        return jdbc.sql("""
                SELECT id, parent_id, node_type, ordinal, created_at FROM document_nodes
                WHERE material_version_id = ? AND id <> ? ORDER BY start_page, node_type
                """).params(versionId, rootId)
                .query((result, row) -> new NodeIdentity(
                        result.getObject("id", UUID.class), result.getObject("parent_id", UUID.class),
                        result.getString("node_type"), result.getInt("ordinal"),
                        result.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private static List<PageIdentity> pageIdentities(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("""
                SELECT id, document_node_id, ordinal, content, extraction_method, quality
                FROM text_blocks WHERE material_version_id = ? ORDER BY ordinal
                """).param(versionId)
                .query((result, row) -> new PageIdentity(
                        result.getObject("id", UUID.class), result.getObject("document_node_id", UUID.class),
                        result.getInt("ordinal"), result.getString("content"),
                        result.getString("extraction_method"), result.getString("quality")))
                .list();
    }

    private static void inTransaction(PlatformTransactionManager manager, Runnable operation) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            operation.run();
        });
    }

    private static Void get(Future<Void> future) throws Throwable {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw exception.getCause();
        }
    }

    private record NodeIdentity(UUID id, UUID parentId, String type, int ordinal, OffsetDateTime createdAt) {}

    private record PageIdentity(
            UUID id, UUID rootId, int ordinal, String content, String method, String quality) {}
}
