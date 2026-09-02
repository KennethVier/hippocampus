package com.hippocampus.materials.infrastructure.persistence;

import java.sql.Types;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;

public final class JdbcProcessingJobStageCompletionRepository
        implements ProcessingJobStageCompletionRepository {
    private static final String COMPLETE_SUCCESSFUL_STAGE = """
            WITH completed AS (
                UPDATE processing_jobs
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :jobId
                  AND status = 'RUNNING'
                  AND job_type = :executedStage
                RETURNING user_id, material_version_id, priority, max_attempts, processing_version
            ), inserted AS (
                INSERT INTO processing_jobs (
                    id, user_id, material_version_id, job_type, status, priority,
                    attempt_count, max_attempts, processing_version, created_at, updated_at
                )
                SELECT :nextJobId, user_id, material_version_id, CAST(:nextStage AS VARCHAR),
                       'PENDING', priority, 0, max_attempts, processing_version,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM completed
                WHERE CAST(:nextStage AS VARCHAR) IS NOT NULL
                RETURNING id
            )
            SELECT EXISTS (SELECT 1 FROM completed)
               AND (
                    CAST(:nextStage AS VARCHAR) IS NULL
                    OR EXISTS (SELECT 1 FROM inserted)
               )
            """;

    private final JdbcClient jdbcClient;

    public JdbcProcessingJobStageCompletionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean completeSuccessfulStage(
            UUID jobId,
            ProcessingJobType executedStage,
            ProcessingJobType nextDurableStage) {
        Boolean completed = jdbcClient.sql(COMPLETE_SUCCESSFUL_STAGE)
                .param("jobId", jobId)
                .param("executedStage", executedStage.name())
                .param("nextJobId", UUID.randomUUID())
                .param("nextStage", nextDurableStage == null ? null : nextDurableStage.name(), Types.VARCHAR)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(completed);
    }
}
