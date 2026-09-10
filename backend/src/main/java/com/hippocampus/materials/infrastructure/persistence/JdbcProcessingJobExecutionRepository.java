package com.hippocampus.materials.infrastructure.persistence;

import java.time.Duration;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

public final class JdbcProcessingJobExecutionRepository implements ProcessingJobExecutionRepository {
    private static final String FENCE = " id=:id AND status='RUNNING' AND locked_by=:worker AND attempt_count=:attempt AND job_type=:type AND material_version_id IS NOT DISTINCT FROM :version AND processing_version=:processingVersion ";
    private final JdbcClient jdbc;
    public JdbcProcessingJobExecutionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public boolean heartbeat(ClaimedProcessingJob job) {
        return fenced("UPDATE processing_jobs SET last_heartbeat_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE" + FENCE, job).update() == 1;
    }
    @Override public boolean progress(ClaimedProcessingJob job, long current, Long total) {
        return fenced("""
                UPDATE processing_jobs SET progress_current=GREATEST(COALESCE(progress_current,:current),:current),
                    progress_total=COALESCE(progress_total,:total),
                    progress=CASE
                        WHEN COALESCE(progress_total,:total) IS NULL THEN progress
                        WHEN COALESCE(progress_total,:total)=0 THEN 100.00
                        ELSE TRUNC((GREATEST(COALESCE(progress_current,:current),:current) * 100.0)
                            / COALESCE(progress_total,:total),2)
                    END,
                    last_heartbeat_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE""" + FENCE + """
                    AND (progress_total IS NULL OR :total IS NULL OR progress_total=:total)
                    AND (COALESCE(progress_total,:total) IS NULL OR :current <= COALESCE(progress_total,:total))
                """, job).param("current", current).param("total", total, Types.BIGINT).update() == 1;
    }
    @Override public boolean retry(ClaimedProcessingJob job, String code, Duration delay) {
        return fenced("UPDATE processing_jobs SET status='RETRY',next_attempt_at=CURRENT_TIMESTAMP + make_interval(secs => :delaySeconds),error_code=:code,error_message=NULL,locked_by=NULL,locked_at=NULL,last_heartbeat_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE"
                + FENCE, job).param("delaySeconds", delay.toSeconds()).param("code", code).update() == 1;
    }
    @Override public boolean fail(ClaimedProcessingJob job, String code) {
        return fenced("UPDATE processing_jobs SET status='FAILED',next_attempt_at=NULL,error_code=:code,error_message=NULL,locked_by=NULL,locked_at=NULL,last_heartbeat_at=NULL,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE"
                + FENCE, job).param("code", code).update() == 1;
    }
    private JdbcClient.StatementSpec fenced(String sql, ClaimedProcessingJob job) {
        return jdbc.sql(sql).param("id", job.jobId()).param("worker", job.workerId())
                .param("attempt", job.attemptNumber()).param("type", job.jobType().name())
                .param("version", job.materialVersionId(), Types.OTHER).param("processingVersion", job.processingVersion());
    }
}
