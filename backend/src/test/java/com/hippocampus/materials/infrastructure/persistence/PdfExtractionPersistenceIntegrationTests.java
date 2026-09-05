package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.ExtractMaterialStageHandler;
import com.hippocampus.materials.application.ExtractPdfNativeText;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingStageCompletionException;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxNativeTextExtractor;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionPersistenceException;
import com.hippocampus.materials.port.PdfNativeTextExtractor;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class PdfExtractionPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void persistsMultipleBatchesIncludingBlankPageAndFinalizesExactly() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            PersistPdfPageBatch batches = context.getBean(PersistPdfPageBatch.class);
            FinalizePdfExtraction finalization = context.getBean(FinalizePdfExtraction.class);

            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            batches.execute(versionId, batch(page(1, "one"), page(2, "")));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            batches.execute(versionId, batch(page(3, "three"), page(4, "four")));
            batches.execute(versionId, batch(page(5, "five")));
            finalization.execute(versionId, 5);

            DocumentStructureRepository structures = context.getBean(DocumentStructureRepository.class);
            List<DocumentNode> nodes = structures.findNodesByMaterialVersion(versionId);
            List<TextBlock> blocks = structures.findTextBlocksByOrdinalRange(versionId, 1, 5);
            assertThat(nodes).singleElement().satisfies(root -> {
                assertThat(root.nodeType().name()).isEqualTo("DOCUMENT");
                assertThat(root.parentId()).isNull();
                assertThat(root.title()).isNull();
                assertThat(root.ordinal()).isNull();
                assertThat(root.startPage()).isEqualTo(1);
                assertThat(root.endPage()).isEqualTo(5);
                assertThat(root.detectionOrigin().name()).isEqualTo("NATIVE");
            });
            assertThat(blocks).extracting(TextBlock::ordinal).containsExactly(1, 2, 3, 4, 5);
            assertThat(blocks).extracting(TextBlock::pageNumber).containsExactly(1, 2, 3, 4, 5);
            assertThat(blocks.get(1).content()).isEmpty();
            assertThat(blocks).allSatisfy(block -> {
                assertThat(block.documentNodeId()).isEqualTo(nodes.getFirst().id());
                assertThat(block.blockType()).isEqualTo(TextBlockType.PAGE_TEXT);
                assertThat(block.extractionMethod()).isEqualTo(TextBlockExtractionMethod.NATIVE);
                assertThat(block.quality()).isNull();
            });
            assertThat(jdbc.sql("SELECT page_count FROM material_versions WHERE id = ?")
                    .param(versionId).query(Integer.class).single()).isEqualTo(5);
            assertThat(jdbc.sql("SELECT extraction_method FROM material_versions WHERE id = ?")
                    .param(versionId).query(String.class).optional()).isEmpty();
        }
    }

    @Test
    void exactBatchAndFinalizationReplayPreserveIdentitiesAndTimestamps() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            PersistPdfPageBatch batches = context.getBean(PersistPdfPageBatch.class);
            FinalizePdfExtraction finalization = context.getBean(FinalizePdfExtraction.class);
            PdfPageBatch batch = batch(page(1, "one"), page(2, "two"));
            batches.execute(versionId, batch);
            finalization.execute(versionId, 2);
            List<BlockIdentity> before = identities(jdbc, versionId);

            batches.execute(versionId, batch);
            finalization.execute(versionId, 2);

            assertThat(identities(jdbc, versionId)).isEqualTo(before);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ?")
                    .param(versionId).query(Integer.class).single()).isEqualTo(2);
        }
    }

    @Test
    void partialReplayReusesPriorPagesAndConflictingContentRollsBackCurrentBatch() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            PersistPdfPageBatch batches = context.getBean(PersistPdfPageBatch.class);
            batches.execute(versionId, batch(page(1, "one"), page(2, "two")));
            List<BlockIdentity> before = identities(jdbc, versionId);

            batches.execute(versionId, batch(page(1, "one"), page(2, "two")));
            batches.execute(versionId, batch(page(3, "three")));
            assertThat(identities(jdbc, versionId).subList(0, 2)).isEqualTo(before);

            assertThatThrownBy(() -> batches.execute(versionId, batch(page(3, "changed"), page(4, "four"))))
                    .isInstanceOf(PdfExtractionPersistenceException.class);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ?")
                    .param(versionId).query(Integer.class).single()).isEqualTo(3);
        }
    }

    @Test
    void rejectsIncompleteOrConflictingFinalizationWithoutChangingDurableState() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            PersistPdfPageBatch batches = context.getBean(PersistPdfPageBatch.class);
            FinalizePdfExtraction finalization = context.getBean(FinalizePdfExtraction.class);
            batches.execute(versionId, batch(page(1, "one")));

            assertThatThrownBy(() -> finalization.execute(versionId, 2))
                    .isInstanceOf(PdfExtractionPersistenceException.class);
            assertThat(pageCount(jdbc, versionId)).isNull();
            assertThat(rootEndPage(jdbc, versionId)).isNull();

            finalization.execute(versionId, 1);
            assertThatThrownBy(() -> finalization.execute(versionId, 2))
                    .isInstanceOf(PdfExtractionPersistenceException.class);
            assertThat(pageCount(jdbc, versionId)).isEqualTo(1);
            assertThat(rootEndPage(jdbc, versionId)).isEqualTo(1);
        }
    }

    @Test
    void deletedMaterialFailsClosedForBatchAndFinalization() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID beforeWrite = insertPdf(jdbc, "PROCESSING");
            deleteMaterial(jdbc, beforeWrite);
            assertThatThrownBy(() -> context.getBean(PersistPdfPageBatch.class)
                            .execute(beforeWrite, batch(page(1, "one"))))
                    .isInstanceOf(PdfExtractionPersistenceException.class);

            UUID beforeFinalization = insertPdf(jdbc, "PROCESSING");
            context.getBean(PersistPdfPageBatch.class).execute(beforeFinalization, batch(page(1, "one")));
            deleteMaterial(jdbc, beforeFinalization);
            assertThatThrownBy(() -> context.getBean(FinalizePdfExtraction.class)
                            .execute(beforeFinalization, 1))
                    .isInstanceOf(PdfExtractionPersistenceException.class);
            assertThat(pageCount(jdbc, beforeFinalization)).isNull();
        }
    }

    @Test
    void databaseRejectsCrossVersionRelationshipsDuplicateRootAndInvalidValues() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID first = insertPdf(jdbc, "PROCESSING");
            UUID second = insertPdf(jdbc, "PROCESSING");
            context.getBean(PersistPdfPageBatch.class).execute(first, batch(page(1, "one")));
            UUID firstRoot = jdbc.sql("SELECT id FROM document_nodes WHERE material_version_id = ?")
                    .param(first).query(UUID.class).single();

            assertIntegrityViolation(() -> jdbc.sql("""
                    INSERT INTO document_nodes
                        (id, material_version_id, parent_id, node_type, ordinal, detection_origin, created_at)
                    VALUES (?, ?, ?, 'SECTION', 1, 'NATIVE', CURRENT_TIMESTAMP)
                    """).params(UUID.randomUUID(), second, firstRoot).update());
            assertIntegrityViolation(() -> jdbc.sql("""
                    INSERT INTO text_blocks
                        (id, material_version_id, document_node_id, page_number, block_type, ordinal,
                         content, extraction_method, created_at)
                    VALUES (?, ?, ?, 1, 'PAGE_TEXT', 1, '', 'NATIVE', CURRENT_TIMESTAMP)
                    """).params(UUID.randomUUID(), second, firstRoot).update());
            assertIntegrityViolation(() -> jdbc.sql("""
                    INSERT INTO document_nodes
                        (id, material_version_id, node_type, start_page, detection_origin, created_at)
                    VALUES (?, ?, 'DOCUMENT', 1, 'NATIVE', CURRENT_TIMESTAMP)
                    """).params(UUID.randomUUID(), first).update());
            UUID self = UUID.randomUUID();
            assertIntegrityViolation(() -> jdbc.sql("""
                    INSERT INTO document_nodes
                        (id, material_version_id, parent_id, node_type, ordinal, detection_origin, created_at)
                    VALUES (?, ?, ?, 'SECTION', 1, 'NATIVE', CURRENT_TIMESTAMP)
                    """).params(self, first, self).update());
            assertIntegrityViolation(() -> jdbc.sql("""
                    INSERT INTO text_blocks
                        (id, material_version_id, page_number, block_type, ordinal, content,
                         extraction_method, created_at)
                    VALUES (?, ?, 0, 'UNKNOWN', 0, '', 'UNKNOWN', CURRENT_TIMESTAMP)
                    """).params(UUID.randomUUID(), second).update());
            assertInvalidNode(jdbc, second, "SECTION", 0, 1, 1, 0L, 0L, "NATIVE");
            assertInvalidNode(jdbc, second, "SECTION", 1, 0, 1, 0L, 0L, "NATIVE");
            assertInvalidNode(jdbc, second, "SECTION", 1, 2, 1, 0L, 0L, "NATIVE");
            assertInvalidNode(jdbc, second, "SECTION", 1, 1, 1, -1L, 0L, "NATIVE");
            assertInvalidNode(jdbc, second, "SECTION", 1, 1, 1, 2L, 1L, "NATIVE");
            assertInvalidNode(jdbc, second, "UNKNOWN", 1, 1, 1, 0L, 0L, "NATIVE");
            assertInvalidNode(jdbc, second, "SECTION", 1, 1, 1, 0L, 0L, "UNKNOWN");
        }
    }

    @Test
    void realExtractionIsDurableBeforeCompletionAndReplaysAfterCompletionFailure() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            UUID jobId = insertRunningExtractionJob(jdbc, versionId);
            byte[] pdf = pdfBytes(List.of("First page", "", "Third page"));
            BinaryObjectStore store = new BinaryObjectStore() {
                @Override
                public void put(
                        com.hippocampus.materials.port.BinaryObjectKey key,
                        java.io.InputStream content,
                        long contentLength) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void get(com.hippocampus.materials.port.BinaryObjectKey key, OutputStream destination) {
                    try {
                        destination.write(pdf);
                    } catch (java.io.IOException exception) {
                        throw new AssertionError(exception);
                    }
                }

                @Override
                public void delete(com.hippocampus.materials.port.BinaryObjectKey key) {
                    throw new UnsupportedOperationException();
                }
            };
            PdfNativeTextExtractor realExtractor = new PdfBoxNativeTextExtractor(
                    store, (input, length) -> new com.hippocampus.materials.port.MaterialContentInspector.Inspection(
                            "application/pdf"), 2, 100, 10_000);
            PdfNativeTextExtractor transactionCheckingExtractor = (source, sink) -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return realExtractor.extract(source, batch -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    sink.accept(batch);
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                });
            };
            ExtractPdfNativeText extraction = new ExtractPdfNativeText(
                    context.getBean(com.hippocampus.materials.port.PdfExtractionSourceRepository.class),
                    transactionCheckingExtractor);
            ExtractMaterialStageHandler handler = new ExtractMaterialStageHandler(
                    extraction, context.getBean(PersistPdfPageBatch.class), context.getBean(FinalizePdfExtraction.class));
            ClaimedProcessingJob job = new ClaimedProcessingJob(
                    jobId, ProcessingJobType.MATERIAL_EXTRACT, versionId, "processor-v1");
            ExecuteClaimedProcessingJob completionFails = new ExecuteClaimedProcessingJob(
                    new ProcessingDispatcher(List.of(handler)),
                    new CompleteProcessingStage((ignoredId, ignoredStage, ignoredNext) -> false));

            assertThatThrownBy(() -> completionFails.execute(job))
                    .isInstanceOf(ProcessingStageCompletionException.class);
            assertThat(pageCount(jdbc, versionId)).isEqualTo(3);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ?")
                    .param(versionId).query(Integer.class).single()).isEqualTo(3);
            assertThat(jdbc.sql("SELECT status FROM processing_jobs WHERE id = ?")
                    .param(jobId).query(String.class).single()).isEqualTo("RUNNING");

            new ExecuteClaimedProcessingJob(
                    new ProcessingDispatcher(List.of(handler)), context.getBean(CompleteProcessingStage.class))
                    .execute(job);

            assertThat(jdbc.sql("SELECT status FROM processing_jobs WHERE id = ?")
                    .param(jobId).query(String.class).single()).isEqualTo("COMPLETED");
            assertThat(jdbc.sql("""
                    SELECT count(*) FROM processing_jobs
                    WHERE material_version_id = ? AND job_type = 'STRUCTURE_DETECT' AND status = 'PENDING'
                    """).param(versionId).query(Integer.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ?")
                    .param(versionId).query(Integer.class).single()).isEqualTo(3);
        }
    }

    @Test
    void concurrentDeletionWinsBeforeEligibilityLockAndPreventsStaleBatchWrite() throws Exception {
        try (var context = startApplicationWithFlyway();
                var deleting = openPostgresConnection()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertPdf(jdbc, "PROCESSING");
            UUID materialId = jdbc.sql("SELECT material_id FROM material_versions WHERE id = ?")
                    .param(versionId).query(UUID.class).single();
            deleting.setAutoCommit(false);
            try (var statement = deleting.prepareStatement(
                    "UPDATE materials SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                statement.setObject(1, materialId);
                statement.executeUpdate();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Void> write = executor.submit(() -> {
                    context.getBean(PersistPdfPageBatch.class).execute(versionId, batch(page(1, "one")));
                    return null;
                });
                deleting.commit();
                assertThatThrownBy(() -> get(write))
                        .isInstanceOf(PdfExtractionPersistenceException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ?")
                        .param(versionId).query(Integer.class).single()).isZero();
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static PdfNativePage page(int number, String content) {
        return new PdfNativePage(number, 612, 792, content);
    }

    private static PdfPageBatch batch(PdfNativePage... pages) {
        return new PdfPageBatch(pages[0].pageNumber(), pages[pages.length - 1].pageNumber(), List.of(pages));
    }

    private static UUID insertPdf(JdbcClient jdbc, String status) {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)")
                .params(userId, userId + "@example.test", now, now).update();
        jdbc.sql("""
                INSERT INTO materials
                    (id, user_id, title, material_type, mime_type, status, created_at, updated_at)
                VALUES (?, ?, 'PDF', 'PDF', 'application/pdf', ?, ?, ?)
                """).params(materialId, userId, status, now, now).update();
        jdbc.sql("""
                INSERT INTO material_versions
                    (id, material_id, version_number, storage_key, file_size_bytes, processing_status, created_at)
                VALUES (?, ?, 1, 'objects/pdf', 42, 'PROCESSING', ?)
                """).params(versionId, materialId, now).update();
        return versionId;
    }

    private static void deleteMaterial(JdbcClient jdbc, UUID versionId) {
        jdbc.sql("""
                UPDATE materials SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT material_id FROM material_versions WHERE id = ?)
                """).param(versionId).update();
    }

    private static UUID insertRunningExtractionJob(JdbcClient jdbc, UUID versionId) {
        UUID jobId = UUID.randomUUID();
        UUID userId = jdbc.sql("""
                SELECT m.user_id FROM materials m
                JOIN material_versions mv ON mv.material_id = m.id WHERE mv.id = ?
                """).param(versionId).query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO processing_jobs
                    (id, user_id, material_version_id, job_type, status, priority, attempt_count,
                     max_attempts, processing_version, created_at, updated_at)
                VALUES (?, ?, ?, 'MATERIAL_EXTRACT', 'RUNNING', 0, 1, 3, 'processor-v1',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).params(jobId, userId, versionId).update();
        return jobId;
    }

    private static byte[] pdfBytes(List<String> pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 720);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static Integer pageCount(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("SELECT page_count FROM material_versions WHERE id = ?")
                .param(versionId)
                .query((result, rowNumber) -> result.getObject(1, Integer.class))
                .single();
    }

    private static Integer rootEndPage(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("SELECT end_page FROM document_nodes WHERE material_version_id = ?")
                .param(versionId)
                .query((result, rowNumber) -> result.getObject(1, Integer.class))
                .single();
    }

    private static List<BlockIdentity> identities(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("""
                SELECT id, ordinal, created_at FROM text_blocks
                WHERE material_version_id = ? ORDER BY ordinal
                """).param(versionId)
                .query((result, rowNumber) -> new BlockIdentity(
                        result.getObject("id", UUID.class), result.getInt("ordinal"),
                        result.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private static void assertIntegrityViolation(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void assertInvalidNode(
            JdbcClient jdbc,
            UUID versionId,
            String nodeType,
            Integer ordinal,
            Integer startPage,
            Integer endPage,
            Long startOffset,
            Long endOffset,
            String origin) {
        assertIntegrityViolation(() -> jdbc.sql("""
                INSERT INTO document_nodes
                    (id, material_version_id, node_type, ordinal, start_page, end_page,
                     start_offset, end_offset, detection_origin, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """).params(
                        UUID.randomUUID(), versionId, nodeType, ordinal, startPage, endPage,
                        startOffset, endOffset, origin)
                .update());
    }

    private static Void get(Future<Void> future) throws Throwable {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw exception.getCause();
        }
    }

    private record BlockIdentity(UUID id, int ordinal, OffsetDateTime createdAt) {}
}
