package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.TableTextDraft;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.TableTextPersistenceException;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class TableTextPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void tableExtractionReplayPreservesNormalizedTabsNewlinesAndCompleteProvenance() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = readyPdf(jdbc, context, 1);
            var persistence = new JdbcTableTextPersistence(jdbc, 10, 1000);
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var table = table(version, root(jdbc, version), 1, 2, "Drug\tDose\nA\t1",
                    TextBlockExtractionMethod.OCR, TextBlockQuality.LIMITED);
            inTransaction(transactions, () -> persistence.persistOrVerify(version, 1, table));
            inTransaction(transactions, () -> persistence.finalizeExtraction(version, 1, 1));
            jdbc.sql("UPDATE text_blocks SET normalized_content = content WHERE material_version_id = ? AND block_type = 'TABLE_TEXT'").param(version).update();
            var before = jdbc.sql("SELECT row_to_json(tb)::text FROM text_blocks tb WHERE material_version_id = ? ORDER BY ordinal").param(version).query(String.class).list();
            inTransaction(transactions, () -> persistence.persistOrVerify(version, 1, table));
            inTransaction(transactions, () -> persistence.finalizeExtraction(version, 1, 1));
            assertThat(jdbc.sql("SELECT row_to_json(tb)::text FROM text_blocks tb WHERE material_version_id = ? ORDER BY ordinal").param(version).query(String.class).list()).isEqualTo(before);
            assertThat(jdbc.sql("SELECT normalized_content FROM text_blocks WHERE material_version_id = ? AND block_type = 'TABLE_TEXT'").param(version).query(String.class).single()).isEqualTo(table.content());
        }
    }

    @Test
    void exactAndPartialReplayConvergeAndPageExtractionReplayLeavesTablesUnchanged() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = insertPdf(jdbc);
            PersistPdfPageBatch pages = context.getBean(PersistPdfPageBatch.class);
            FinalizePdfExtraction pageFinalization = context.getBean(FinalizePdfExtraction.class);
            pages.execute(version, new PdfPageBatch(1, 2, List.of(page(1), page(2))));
            pageFinalization.execute(version, 2);
            UUID root = root(jdbc, version);
            JdbcTableTextPersistence persistence = new JdbcTableTextPersistence(jdbc, 10, 1000);
            TransactionTemplate transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            TableTextDraft first = table(version, root, 1, 3, "Drug\tDose\tEffect\nA\t1\tB",
                    TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG);
            TableTextDraft second = table(version, root, 2, 4, "Complex heading\nA B C",
                    TextBlockExtractionMethod.NATIVE, TextBlockQuality.LIMITED);

            inTransaction(transactions, () -> persistence.persistOrVerify(version, 2, first));
            List<RowIdentity> partial = identities(jdbc, version);
            inTransaction(transactions, () -> persistence.persistOrVerify(version, 2, first));
            inTransaction(transactions, () -> persistence.persistOrVerify(version, 2, second));
            inTransaction(transactions, () -> persistence.finalizeExtraction(version, 2, 2));
            List<RowIdentity> complete = identities(jdbc, version);

            assertThat(complete.subList(0, 1)).isEqualTo(partial);
            pageFinalization.execute(version, 2);
            assertThat(identities(jdbc, version)).isEqualTo(complete);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ? AND block_type = 'PAGE_TEXT'")
                    .param(version).query(Integer.class).single()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = ? AND block_type = 'TABLE_TEXT'")
                    .param(version).query(Integer.class).single()).isEqualTo(2);
        }
    }

    @Test
    void conflictsWrongProvenanceAndUnexpectedDurableRowsFailClosed() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = readyPdf(jdbc, context, 1);
            UUID other = readyPdf(jdbc, context, 1);
            UUID root = root(jdbc, version);
            UUID otherRoot = root(jdbc, other);
            JdbcTableTextPersistence persistence = new JdbcTableTextPersistence(jdbc, 10, 1000);
            TransactionTemplate transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            TableTextDraft expected = table(version, root, 1, 2, "A\tB\tC\n1\t2\t3\n4\t5\t6",
                    TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG);
            inTransaction(transactions, () -> persistence.persistOrVerify(version, 1, expected));

            assertThatThrownBy(() -> inTransaction(transactions, () -> persistence.persistOrVerify(
                    version, 1, table(version, root, 1, 2, "changed",
                            TextBlockExtractionMethod.NATIVE, TextBlockQuality.LIMITED))))
                    .isInstanceOf(TableTextPersistenceException.class);
            assertThatThrownBy(() -> inTransaction(transactions, () -> persistence.persistOrVerify(
                    version, 1, table(version, otherRoot, 1, 3, "foreign",
                            TextBlockExtractionMethod.NATIVE, TextBlockQuality.LIMITED))))
                    .isInstanceOf(TableTextPersistenceException.class);
            assertThatThrownBy(() -> inTransaction(transactions, () -> persistence.persistOrVerify(
                    version, 1, table(version, root, 2, 3, "bad page",
                            TextBlockExtractionMethod.NATIVE, TextBlockQuality.LIMITED))))
                    .isInstanceOf(TableTextPersistenceException.class);
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.finalizeExtraction(version, 1, 0)))
                    .isInstanceOf(TableTextPersistenceException.class);
            assertThat(jdbc.sql("SELECT content FROM text_blocks WHERE material_version_id = ? AND ordinal = 2")
                    .params(version).query(String.class).single()).isEqualTo(expected.content());
        }
    }

    @Test
    void rejectsSameVersionNodeThatDoesNotContainTheTablePageDuringPersistAndFinalization() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = readyPdf(jdbc, context, 2);
            UUID section = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO document_nodes
                        (id, material_version_id, parent_id, node_type, title, ordinal,
                         start_page, end_page, detection_origin, created_at)
                    VALUES (?, ?, ?, 'SECTION', 'Page one', 1, 1, 1, 'NATIVE', CURRENT_TIMESTAMP)
                    """).params(section, version, root(jdbc, version)).update();
            JdbcTableTextPersistence persistence = new JdbcTableTextPersistence(jdbc, 10, 1000);
            TransactionTemplate transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            assertThatThrownBy(() -> inTransaction(transactions, () -> persistence.persistOrVerify(
                    version, 2, table(version, section, 2, 3, "A\tB\n1\t2",
                            TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG))))
                    .isInstanceOf(TableTextPersistenceException.class);

            jdbc.sql("""
                    INSERT INTO text_blocks
                        (id, material_version_id, document_node_id, page_number, block_type,
                         ordinal, content, extraction_method, quality, created_at)
                    VALUES (?, ?, ?, 2, 'TABLE_TEXT', 3, 'A B', 'NATIVE', 'LIMITED', CURRENT_TIMESTAMP)
                    """).params(UUID.randomUUID(), version, section).update();
            assertThatThrownBy(() -> inTransaction(transactions,
                    () -> persistence.finalizeExtraction(version, 2, 1)))
                    .isInstanceOf(TableTextPersistenceException.class);
        }
    }

    private static UUID readyPdf(JdbcClient jdbc, ConfigurableApplicationContext context, int pageCount) {
        UUID version = insertPdf(jdbc);
        List<PdfExtractedPage> extracted = java.util.stream.IntStream.rangeClosed(1, pageCount)
                .mapToObj(TableTextPersistenceIntegrationTests::page).toList();
        context.getBean(PersistPdfPageBatch.class).execute(
                version, new PdfPageBatch(1, pageCount, extracted));
        context.getBean(FinalizePdfExtraction.class).execute(version, pageCount);
        return version;
    }

    private static PdfExtractedPage page(int number) {
        return new PdfExtractedPage(number, "page " + number, TextBlockExtractionMethod.NATIVE, null);
    }

    private static TableTextDraft table(
            UUID version, UUID node, int page, int ordinal, String content,
            TextBlockExtractionMethod method, TextBlockQuality quality) {
        return new TableTextDraft(version, node, page, ordinal, content, method, quality);
    }

    private static UUID insertPdf(JdbcClient jdbc) {
        UUID user = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)")
                .params(user, user + "@example.test", now, now).update();
        jdbc.sql("""
                INSERT INTO materials
                    (id, user_id, title, material_type, mime_type, status, created_at, updated_at)
                VALUES (?, ?, 'PDF', 'PDF', 'application/pdf', 'PROCESSING', ?, ?)
                """).params(material, user, now, now).update();
        jdbc.sql("""
                INSERT INTO material_versions
                    (id, material_id, version_number, storage_key, file_size_bytes, processing_status, created_at)
                VALUES (?, ?, 1, 'objects/pdf', 42, 'PROCESSING', ?)
                """).params(version, material, now).update();
        return version;
    }

    private static UUID root(JdbcClient jdbc, UUID version) {
        return jdbc.sql("SELECT id FROM document_nodes WHERE material_version_id = ? AND node_type = 'DOCUMENT'")
                .param(version).query(UUID.class).single();
    }

    private static List<RowIdentity> identities(JdbcClient jdbc, UUID version) {
        return jdbc.sql("""
                SELECT id, ordinal, content FROM text_blocks
                WHERE material_version_id = ? AND block_type = 'TABLE_TEXT' ORDER BY ordinal
                """).param(version).query((result, row) -> new RowIdentity(
                        result.getObject("id", UUID.class), result.getInt("ordinal"), result.getString("content"))).list();
    }

    private static void inTransaction(TransactionTemplate transactions, Runnable operation) {
        transactions.executeWithoutResult(status -> operation.run());
    }

    private record RowIdentity(UUID id, int ordinal, String content) {}
}
