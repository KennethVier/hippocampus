package com.hippocampus.materials.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;

public final class JdbcPdfExtractionSourceRepository implements PdfExtractionSourceRepository {
    private static final String FIND_SOURCE = """
            SELECT mv.storage_key, mv.file_size_bytes, m.status, m.material_type, m.mime_type
            FROM material_versions mv
            JOIN materials m ON m.id = mv.material_id
            WHERE mv.id = :materialVersionId
            """;

    private final JdbcClient jdbcClient;

    public JdbcPdfExtractionSourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PdfExtractionSource requireExtractablePdf(UUID materialVersionId) {
        if (materialVersionId == null) {
            throw new IllegalArgumentException("materialVersionId must not be null");
        }
        SourceRow row = jdbcClient.sql(FIND_SOURCE)
                .param("materialVersionId", materialVersionId)
                .query((result, rowNumber) -> new SourceRow(
                        result.getString("storage_key"),
                        result.getObject("file_size_bytes", Long.class),
                        result.getString("status"),
                        result.getString("material_type"),
                        result.getString("mime_type")))
                .optional()
                .orElseThrow(() -> new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE));
        if ("DELETED".equals(row.status())
                || !"PDF".equals(row.materialType())
                || !"application/pdf".equals(row.mimeType())) {
            throw new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE);
        }
        if (row.storageKey() == null || row.storageKey().isBlank()
                || row.fileSizeBytes() == null || row.fileSizeBytes() <= 0) {
            throw new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE);
        }
        try {
            return new PdfExtractionSource(
                    materialVersionId, new BinaryObjectKey(row.storageKey()), row.fileSizeBytes());
        } catch (IllegalArgumentException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE, exception);
        }
    }

    private record SourceRow(
            String storageKey, Long fileSizeBytes, String status, String materialType, String mimeType) {}
}
