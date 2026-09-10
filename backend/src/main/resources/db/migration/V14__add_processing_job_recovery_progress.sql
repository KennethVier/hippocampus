ALTER TABLE processing_jobs
    ADD COLUMN progress_current BIGINT NULL,
    ADD COLUMN progress_total BIGINT NULL,
    ADD CONSTRAINT chk_processing_jobs_progress_counts CHECK (
        (progress_current IS NULL OR progress_current >= 0)
        AND (progress_total IS NULL OR progress_total >= 0)
        AND (progress_current IS NULL OR progress_total IS NULL OR progress_current <= progress_total)
    );

CREATE INDEX idx_processing_jobs_retry_due
    ON processing_jobs (next_attempt_at, created_at, id)
    WHERE status = 'RETRY' AND attempt_count < max_attempts;

CREATE INDEX idx_processing_jobs_running_heartbeat
    ON processing_jobs ((COALESCE(last_heartbeat_at, locked_at, started_at, updated_at, created_at)), id)
    WHERE status = 'RUNNING' AND attempt_count < max_attempts;
