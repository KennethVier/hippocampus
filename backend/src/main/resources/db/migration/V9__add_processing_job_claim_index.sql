CREATE INDEX idx_processing_jobs_pending_claim_fifo
    ON processing_jobs (created_at, id)
    WHERE status = 'PENDING'
      AND attempt_count < max_attempts;
