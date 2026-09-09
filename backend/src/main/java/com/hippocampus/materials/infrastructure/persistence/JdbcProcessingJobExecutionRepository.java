package com.hippocampus.materials.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

public final class JdbcProcessingJobExecutionRepository implements ProcessingJobExecutionRepository {
    private static final String FENCE = " id=:id AND status='RUNNING' AND locked_by=:worker AND attempt_count=:attempt ";
    private final JdbcClient jdbc;
    public JdbcProcessingJobExecutionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public boolean heartbeat(ClaimedProcessingJob job, Instant now) {
        return fenced("UPDATE processing_jobs SET last_heartbeat_at=:now,updated_at=:now WHERE" + FENCE, job, now).update() == 1;
    }
    @Override public boolean progress(ClaimedProcessingJob job, long current, Long total, Instant now) {
        return fenced("""
                UPDATE processing_jobs SET progress_current=:current,
                    progress_total=COALESCE(progress_total,:total),
                    progress=CASE
                        WHEN COALESCE(progress_total,:total) IS NULL THEN progress
                        WHEN COALESCE(progress_total,:total)=0 THEN 100.00
                        ELSE TRUNC((:current * 100.0) / COALESCE(progress_total,:total),2)
                    END,
                    last_heartbeat_at=:now,updated_at=:now WHERE""" + FENCE + """
                    AND (progress_current IS NULL OR progress_current <= :current)
                    AND (progress_total IS NULL OR :total IS NULL OR progress_total=:total)
                    AND (COALESCE(progress_total,:total) IS NULL OR :current <= COALESCE(progress_total,:total))
                """, job, now).param("current", current).param("total", total, Types.BIGINT).update() == 1;
    }
    @Override public boolean retry(ClaimedProcessingJob job, String code, Instant next, Instant now) {
        return fenced("UPDATE processing_jobs SET status='RETRY',next_attempt_at=:next,error_code=:code,error_message=NULL,locked_by=NULL,locked_at=NULL,last_heartbeat_at=NULL,updated_at=:now WHERE"
                + FENCE, job, now).param("next", timestamp(next)).param("code", code).update() == 1;
    }
    @Override public boolean fail(ClaimedProcessingJob job, String code, Instant now) {
        return fenced("UPDATE processing_jobs SET status='FAILED',next_attempt_at=NULL,error_code=:code,error_message=NULL,locked_by=NULL,locked_at=NULL,last_heartbeat_at=NULL,completed_at=:now,updated_at=:now WHERE"
                + FENCE, job, now).param("code", code).update() == 1;
    }
    private JdbcClient.StatementSpec fenced(String sql, ClaimedProcessingJob job, Instant now) {
        return jdbc.sql(sql).param("id", job.jobId()).param("worker", job.workerId())
                .param("attempt", job.attemptNumber()).param("now", timestamp(now));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
