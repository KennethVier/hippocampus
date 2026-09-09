package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.application.NormalizeMaterialText;
import com.hippocampus.materials.domain.*;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class TextNormalizationPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private JdbcTextNormalizationRepository repository;
    private TransactionTemplate transactions;
    private UUID version;

    @BeforeEach
    void setup() throws SQLException {
        resetPostgresSchema();
        context = startApplicationWithFlyway();
        jdbc = context.getBean(JdbcClient.class);
        repository = new JdbcTextNormalizationRepository(jdbc, 20, 30, 40);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        version = readyPdf();
    }

    @AfterEach
    void close() { if (context != null) context.close(); }

    @Test
    void initialPartialAndExactReplayPreserveEverySourceFieldAndIdentity() {
        List<TextBlock> raw = repository.findPageText(version, 1, 2);
        write(List.of(normalized(raw.getFirst(), "joined")));
        var first = snapshot();
        write(List.of(normalized(raw.getFirst(), "joined")));
        assertThat(snapshot()).isEqualTo(first);
        write(List.of(normalized(raw.getFirst(), "joined"), normalized(raw.getLast(), "")));
        finish();
        List<TextBlock> actual = repository.findPageText(version, 1, 2);
        for (int i = 0; i < raw.size(); i++) {
            assertThat(normalized(actual.get(i), null)).isEqualTo(raw.get(i));
        }
        assertThat(actual).extracting(TextBlock::normalizedContent).containsExactly("joined", "");
    }

    @Test
    void conflictingRetryRollsBackWholeBatch() {
        var raw = repository.findPageText(version, 1, 2);
        write(List.of(normalized(raw.getLast(), "")));
        var before = snapshot();
        assertThatThrownBy(() -> write(List.of(normalized(raw.getFirst(), "new"), normalized(raw.getLast(), "conflict"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsCrossVersionAndWrongSameVersionNodeAndEveryChangedSourceField() {
        TextBlock source = repository.findPageText(version, 1, 1).getFirst();
        UUID other = readyPdf();
        TextBlock foreign = repository.findPageText(other, 1, 1).getFirst();
        assertThatThrownBy(() -> write(List.of(normalized(foreign, "x")))).isInstanceOf(IllegalStateException.class);
        UUID section = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes (id, material_version_id, node_type, start_page, end_page, detection_origin, created_at) VALUES (?, ?, 'SECTION', 1, 2, 'NATIVE', CURRENT_TIMESTAMP)")
                .params(section, version).update();
        List<TextBlock> changed = List.of(
            new TextBlock(UUID.randomUUID(), version, source.documentNodeId(), 1, TextBlockType.PAGE_TEXT, 1, source.content(), TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, section, 1, TextBlockType.PAGE_TEXT, 1, source.content(), TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 2, TextBlockType.PAGE_TEXT, 1, source.content(), TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 1, TextBlockType.TABLE_TEXT, 1, source.content(), TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 1, TextBlockType.PAGE_TEXT, 3, source.content(), TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 1, TextBlockType.PAGE_TEXT, 1, "changed", TextBlockExtractionMethod.NATIVE, null, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 1, TextBlockType.PAGE_TEXT, 1, source.content(), TextBlockExtractionMethod.OCR, TextBlockQuality.POOR, source.createdAt(), "x"),
            new TextBlock(source.id(), version, source.documentNodeId(), 1, TextBlockType.PAGE_TEXT, 1, source.content(), TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG, source.createdAt(), "x"));
        var before = snapshot();
        for (TextBlock block : changed) {
            assertThatThrownBy(() -> write(List.of(block))).isInstanceOf(IllegalStateException.class);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE materials SET status = 'DELETED'",
        "UPDATE materials SET material_type = 'TEXT'",
        "UPDATE materials SET mime_type = 'text/plain'",
        "UPDATE material_versions SET storage_key = NULL",
        "UPDATE material_versions SET storage_key = '   '",
        "UPDATE material_versions SET file_size_bytes = 0",
        "UPDATE material_versions SET file_size_bytes = NULL",
        "UPDATE material_versions SET page_count = 0",
        "UPDATE material_versions SET page_count = NULL",
        "DELETE FROM material_versions"
    })
    void persistenceAndFinalizationRejectCurrentIneligibleState(String mutation) {
        var blocks = normalizedPages();
        write(blocks);
        jdbc.sql(mutation).update();
        assertThatThrownBy(() -> write(blocks)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(this::finish).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "DELETE FROM text_blocks WHERE ordinal = 2",
        "UPDATE text_blocks SET normalized_content = NULL WHERE ordinal = 2",
        "UPDATE text_blocks SET page_number = 1 WHERE ordinal = 2",
        "UPDATE text_blocks SET page_number = NULL WHERE ordinal = 2",
        "UPDATE text_blocks SET ordinal = 3 WHERE ordinal = 2",
        "UPDATE text_blocks SET quality = 'STRONG' WHERE ordinal = 1",
        "UPDATE text_blocks SET extraction_method = 'OCR', quality = NULL WHERE ordinal = 1",
        "UPDATE text_blocks SET document_node_id = NULL WHERE ordinal = 1",
        "UPDATE document_nodes SET end_page = 1",
        "UPDATE document_nodes SET start_page = NULL",
        "UPDATE text_blocks SET block_type = 'PARAGRAPH' WHERE ordinal = 1",
        "UPDATE material_versions SET page_count = 3"
    })
    void finalizationIndependentlyRejectsMalformedPageState(String mutation) {
        write(normalizedPages());
        finish();
        jdbc.sql(mutation).update();
        assertThatThrownBy(this::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void persistenceRejectsDurableNodePageContainmentMismatch() {
        jdbc.sql("UPDATE document_nodes SET end_page = 1").update();
        var page = repository.findPageText(version, 2, 2).getFirst();
        assertThatThrownBy(() -> write(List.of(normalized(page, "")))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validTablesAndOcrMetadataRemainExactAndZeroTablesAndBlankPagesFinalize() {
        write(normalizedPages());
        finish();
        for (String methodQuality : List.of("'NATIVE', 'STRONG'", "'NATIVE', 'LIMITED'", "'OCR', 'LIMITED'", "'OCR', 'POOR'")) {
            insertTable(methodQuality);
            var raw = repository.findTableText(version, 1, 2).getFirst();
            write(List.of(normalized(raw, raw.content())));
            finish();
            assertThat(normalized(repository.findTableText(version, 1, 2).getFirst(), null)).isEqualTo(raw);
            jdbc.sql("DELETE FROM text_blocks WHERE block_type = 'TABLE_TEXT'").update();
        }
        for (String quality : List.of("STRONG", "LIMITED", "POOR")) {
            jdbc.sql("UPDATE text_blocks SET extraction_method = 'OCR', quality = ? WHERE ordinal = 1").param(quality).update();
            finish();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE text_blocks SET quality = NULL WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET quality = 'POOR' WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET extraction_method = 'OCR', quality = 'STRONG' WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET normalized_content = 'changed' WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET normalized_content = NULL WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET document_node_id = NULL WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET page_number = 3 WHERE block_type = 'TABLE_TEXT'",
        "UPDATE text_blocks SET page_number = NULL WHERE block_type = 'TABLE_TEXT'"
    })
    void finalizationRejectsMalformedTables(String mutation) {
        write(normalizedPages());
        insertTable("'NATIVE', 'STRONG'");
        var table = repository.findTableText(version, 1, 2).getFirst();
        write(List.of(normalized(table, table.content())));
        finish();
        jdbc.sql(mutation).update();
        assertThatThrownBy(this::finish).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NATIVE", "OCR", "TABLE"})
    void rawAndNormalizedValuesUseTheirOwnSourceBound(String type) {
        int limit = type.equals("TABLE") ? 40 : type.equals("OCR") ? 30 : 20;
        if (type.equals("TABLE")) insertTable("'NATIVE', 'STRONG'");
        int ordinal = type.equals("TABLE") ? 3 : 1;
        if (type.equals("OCR")) jdbc.sql("UPDATE text_blocks SET extraction_method = 'OCR', quality = 'LIMITED' WHERE ordinal = 1").update();
        jdbc.sql("UPDATE text_blocks SET content = ? WHERE ordinal = ?").params("a".repeat(limit), ordinal).update();
        TextBlock source = type.equals("TABLE") ? repository.findTableText(version, 1, 2).getFirst() : repository.findPageText(version, 1, 1).getFirst();
        assertThatThrownBy(() -> write(List.of(normalized(source, "a".repeat(limit + 1))))).isInstanceOf(IllegalStateException.class);
        write(List.of(normalized(source, source.content())));
        jdbc.sql("UPDATE text_blocks SET content = ?, normalized_content = NULL WHERE ordinal = ?").params("a".repeat(limit + 1), ordinal).update();
        TextBlock oversized = type.equals("TABLE") ? repository.findTableText(version, 1, 2).getFirst() : repository.findPageText(version, 1, 1).getFirst();
        assertThatThrownBy(() -> write(List.of(normalized(oversized, type.equals("TABLE") ? oversized.content() : "short"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("source bound");
        assertThat(jdbc.sql("SELECT count(*) FROM text_blocks WHERE ordinal = ? AND normalized_content IS NULL").param(ordinal).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void actualApplicationUsesTransactionalPersistenceWithoutDocumentWideTransaction() {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        context.getBean(NormalizeMaterialText.class).execute(version);
        finish();
        assertThat(repository.findPageText(version, 1, 2)).extracting(TextBlock::normalizedContent).containsExactly("line one line two", "");
        assertThatThrownBy(() -> repository.persistOrVerify(version, normalizedPages())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.finalizeNormalization(version, 2)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"persist-material", "persist-version", "finalize-material", "finalize-version"})
    void durableEligibilityCannotChangeUntilShortTransactionCompletes(String scenario) throws SQLException {
        write(normalizedPages());
        try (var concurrent = openPostgresConnection(); var statement = concurrent.createStatement()) {
            statement.execute("SET lock_timeout = '150ms'");
            transactions.executeWithoutResult(status -> {
                if (scenario.startsWith("persist")) repository.persistOrVerify(version, normalizedPages());
                else repository.finalizeNormalization(version, 2);
                String mutation = scenario.endsWith("material")
                        ? "UPDATE materials SET status = 'DELETED'"
                        : "UPDATE material_versions SET storage_key = NULL";
                assertThatThrownBy(() -> statement.executeUpdate(mutation))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55P03"));
            });
            finish();
        }
    }

    @Test
    void tableNodeMustContainItsPageEvenWhenNodeBelongsToSameVersion() {
        write(normalizedPages());
        insertTable("'NATIVE', 'STRONG'");
        var table = repository.findTableText(version, 1, 2).getFirst();
        write(List.of(normalized(table, table.content())));
        UUID node = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes (id, material_version_id, node_type, start_page, end_page, detection_origin, created_at) VALUES (?, ?, 'SECTION', 1, 1, 'NATIVE', CURRENT_TIMESTAMP)").params(node, version).update();
        jdbc.sql("UPDATE text_blocks SET document_node_id = ? WHERE block_type = 'TABLE_TEXT'").param(node).update();
        var bad = repository.findTableText(version, 1, 2).getFirst();
        assertThatThrownBy(() -> write(List.of(bad))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(this::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tablePersistenceRejectsAnyChangeToRawTabsOrNewlines() {
        insertTable("'NATIVE', 'STRONG'");
        var table = repository.findTableText(version, 1, 2).getFirst();
        var before = snapshot();
        assertThatThrownBy(() -> write(List.of(normalized(table, table.content().replace('\t', ' ')))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    private UUID readyPdf() {
        UUID user = UUID.randomUUID(), material = UUID.randomUUID(), result = UUID.randomUUID();
        jdbc.sql("INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").params(user, user + "@example.test").update();
        jdbc.sql("INSERT INTO materials (id, user_id, title, material_type, mime_type, status, created_at, updated_at) VALUES (?, ?, 'PDF', 'PDF', 'application/pdf', 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").params(material, user).update();
        jdbc.sql("INSERT INTO material_versions (id, material_id, version_number, storage_key, file_size_bytes, processing_status, created_at) VALUES (?, ?, 1, 'objects/pdf', 42, 'PROCESSING', CURRENT_TIMESTAMP)").params(result, material).update();
        context.getBean(PersistPdfPageBatch.class).execute(result, new PdfPageBatch(1, 2, List.of(
            new PdfExtractedPage(1, "line one\nline two", TextBlockExtractionMethod.NATIVE, null),
            new PdfExtractedPage(2, "", TextBlockExtractionMethod.NATIVE, null))));
        context.getBean(FinalizePdfExtraction.class).execute(result, 2);
        return result;
    }

    private void insertTable(String methodQuality) {
        jdbc.sql("INSERT INTO text_blocks (id, material_version_id, document_node_id, page_number, block_type, ordinal, content, extraction_method, quality, created_at) SELECT ?, ?, id, 2, 'TABLE_TEXT', 3, ?, " + methodQuality + ", CURRENT_TIMESTAMP FROM document_nodes WHERE material_version_id = ? AND node_type = 'DOCUMENT'")
                .params(UUID.randomUUID(), version, "Drug\tDose\nA\t1", version).update();
    }
    private List<TextBlock> normalizedPages() {
        return repository.findPageText(version, 1, 2).stream().map(b -> normalized(b, b.content())).toList();
    }
    private void write(List<TextBlock> blocks) { transactions.executeWithoutResult(s -> repository.persistOrVerify(version, blocks)); }
    private void finish() { transactions.executeWithoutResult(s -> repository.finalizeNormalization(version, 2)); }
    private List<String> snapshot() {
        return jdbc.sql("SELECT row_to_json(tb)::text FROM text_blocks tb WHERE material_version_id = ? ORDER BY ordinal").param(version).query(String.class).list();
    }
    private static TextBlock normalized(TextBlock b, String value) {
        return new TextBlock(b.id(), b.materialVersionId(), b.documentNodeId(), b.pageNumber(), b.blockType(), b.ordinal(), b.content(), b.extractionMethod(), b.quality(), b.createdAt(), value);
    }
}
