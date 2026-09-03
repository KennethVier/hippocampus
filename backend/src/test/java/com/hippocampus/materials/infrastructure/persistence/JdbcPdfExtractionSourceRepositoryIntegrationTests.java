package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcPdfExtractionSourceRepositoryIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void resolvesOnlyAuthoritativeCurrentlyExtractablePdfSource() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID versionId = insertSource(jdbc, "ACTIVE", "PDF", "application/pdf", "objects/source", 42L);
            JdbcPdfExtractionSourceRepository repository = new JdbcPdfExtractionSourceRepository(jdbc);

            PdfExtractionSource source = repository.requireExtractablePdf(versionId);

            assertThat(source.materialVersionId()).isEqualTo(versionId);
            assertThat(source.objectKey().value()).isEqualTo("objects/source");
            assertThat(source.fileSizeBytes()).isEqualTo(42);
        }
    }

    @Test
    void failsClosedForMissingDeletedWrongTypeOrIncompleteSource() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            JdbcPdfExtractionSourceRepository repository = new JdbcPdfExtractionSourceRepository(jdbc);

            assertFailure(repository, UUID.randomUUID(), PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE);
            assertFailure(repository, insertSource(jdbc, "DELETED", "PDF", "application/pdf", "objects/a", 42L),
                    PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE);
            assertFailure(repository, insertSource(jdbc, "ACTIVE", "TEXT", "text/plain", "objects/b", 42L),
                    PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE);
            assertFailure(repository, insertSource(jdbc, "ACTIVE", "PDF", "text/plain", "objects/c", 42L),
                    PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE);
            assertFailure(repository, insertSource(jdbc, "ACTIVE", "PDF", "application/pdf", null, 42L),
                    PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE);
            assertFailure(repository, insertSource(jdbc, "ACTIVE", "PDF", "application/pdf", "objects/d", 0L),
                    PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE);
        }
    }

    private static UUID insertSource(
            JdbcClient jdbc, String status, String type, String mime, String key, Long size) {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO users (id, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)")
                .params(userId, userId + "@example.test", now, now).update();
        jdbc.sql("""
                INSERT INTO materials
                    (id, user_id, title, material_type, mime_type, status, created_at, updated_at)
                VALUES (?, ?, 'source', ?, ?, ?, ?, ?)
                """).params(materialId, userId, type, mime, status, now, now).update();
        jdbc.sql("""
                INSERT INTO material_versions
                    (id, material_id, version_number, storage_key, file_size_bytes, processing_status, created_at)
                VALUES (?, ?, 1, ?, ?, 'UPLOADED', ?)
                """).params(versionId, materialId, key, size, now).update();
        return versionId;
    }

    private static void assertFailure(
            JdbcPdfExtractionSourceRepository repository,
            UUID versionId,
            PdfExtractionException.Kind expected) {
        assertThatThrownBy(() -> repository.requireExtractablePdf(versionId))
                .isInstanceOfSatisfying(PdfExtractionException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(expected));
    }
}
