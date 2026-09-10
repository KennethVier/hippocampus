package com.hippocampus.materials.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.port.MaterialProcessingStateRepository;

public final class JdbcMaterialProcessingStateRepository implements MaterialProcessingStateRepository {
    private static final Map<String, String> PUBLIC_STAGES = Map.of(
            "MATERIAL_VALIDATE", "VALIDATING",
            "MATERIAL_EXTRACT", "EXTRACTING",
            "STRUCTURE_DETECT", "STRUCTURE_DETECTION",
            "VISUAL_EXTRACT", "VISUAL_PROCESSING",
            "NORMALIZE", "TEXT_NORMALIZATION",
            "CHUNK", "CHUNKING");

    private final JdbcClient jdbc;

    public JdbcMaterialProcessingStateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DurableProcessingState> findCurrentPhaseThreeState(UUID materialVersionId) {
        return jdbc.sql("""
                SELECT job_type, progress, progress_current, progress_total
                FROM processing_jobs
                WHERE material_version_id = :version
                  AND status IN ('RUNNING', 'RETRY', 'PENDING')
                  AND job_type IN (
                    'MATERIAL_VALIDATE', 'MATERIAL_EXTRACT', 'STRUCTURE_DETECT',
                    'VISUAL_EXTRACT', 'NORMALIZE', 'CHUNK'
                  )
                ORDER BY CASE status
                    WHEN 'RUNNING' THEN 0
                    WHEN 'RETRY' THEN 1
                    WHEN 'PENDING' THEN 2
                    ELSE 3
                  END,
                  CASE job_type
                    WHEN 'MATERIAL_VALIDATE' THEN 0
                    WHEN 'MATERIAL_EXTRACT' THEN 1
                    WHEN 'STRUCTURE_DETECT' THEN 2
                    WHEN 'VISUAL_EXTRACT' THEN 3
                    WHEN 'NORMALIZE' THEN 4
                    WHEN 'CHUNK' THEN 5
                    ELSE 6
                  END,
                  created_at,
                  id
                LIMIT 1
                """)
                .param("version", materialVersionId)
                .query((result, rowNumber) -> {
                    String publicStage = PUBLIC_STAGES.get(result.getString("job_type"));
                    BigDecimal progress = result.getBigDecimal("progress");
                    Long current = result.getObject("progress_current", Long.class);
                    Long total = result.getObject("progress_total", Long.class);
                    return new DurableProcessingState(publicStage, progress, current, total);
                })
                .optional();
    }
}
