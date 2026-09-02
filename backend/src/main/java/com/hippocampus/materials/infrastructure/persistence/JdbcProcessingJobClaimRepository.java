package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobClaimRepository;

public final class JdbcProcessingJobClaimRepository implements ProcessingJobClaimRepository {
    private static final String CLAIM_NEXT_ELIGIBLE = """
            WITH candidate AS (
                SELECT pj.id
                FROM processing_jobs pj
                WHERE pj.status = 'PENDING'
                  AND pj.attempt_count < pj.max_attempts
                  AND (
                      pj.material_version_id IS NULL
                      OR EXISTS (
                          SELECT 1
                          FROM material_versions mv
                          JOIN materials m ON m.id = mv.material_id
                          WHERE mv.id = pj.material_version_id
                            AND m.user_id = pj.user_id
                            AND m.status <> 'DELETED'
                      )
                )
                ORDER BY pj.created_at ASC, pj.id ASC
                LIMIT 1
                FOR UPDATE OF pj SKIP LOCKED
            )
            UPDATE processing_jobs pj
            SET status = 'RUNNING',
                attempt_count = pj.attempt_count + 1,
                locked_at = CURRENT_TIMESTAMP,
                locked_by = :workerId,
                started_at = COALESCE(pj.started_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP
            FROM candidate
            WHERE pj.id = candidate.id
            RETURNING pj.id, pj.job_type, pj.material_version_id, pj.processing_version
            """;

    private final JdbcClient jdbcClient;

    public JdbcProcessingJobClaimRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ClaimedProcessingJob> claimNextEligible(String workerId) {
        return jdbcClient.sql(CLAIM_NEXT_ELIGIBLE)
                .param("workerId", workerId)
                .query(JdbcProcessingJobClaimRepository::mapClaim)
                .optional();
    }

    private static ClaimedProcessingJob mapClaim(ResultSet result, int rowNumber) throws SQLException {
        return new ClaimedProcessingJob(
                result.getObject("id", UUID.class),
                ProcessingJobType.valueOf(result.getString("job_type")),
                result.getObject("material_version_id", UUID.class),
                result.getString("processing_version"));
    }
}
