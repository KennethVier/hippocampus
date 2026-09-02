CREATE TABLE processing_jobs (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    material_version_id UUID NULL,
    job_type VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    priority INT NOT NULL,
    progress NUMERIC(5,2) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    locked_at TIMESTAMPTZ NULL,
    locked_by VARCHAR NULL,
    next_attempt_at TIMESTAMPTZ NULL,
    last_heartbeat_at TIMESTAMPTZ NULL,
    processing_version VARCHAR NOT NULL,
    error_code VARCHAR NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_processing_jobs PRIMARY KEY (id),
    CONSTRAINT fk_processing_jobs_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_jobs_material_version FOREIGN KEY (material_version_id)
        REFERENCES material_versions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_processing_jobs_status CHECK (status IN (
        'PENDING', 'RUNNING', 'RETRY', 'COMPLETED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT chk_processing_jobs_job_type CHECK (job_type IN (
        'MATERIAL_VALIDATE', 'MATERIAL_EXTRACT', 'STRUCTURE_DETECT', 'VISUAL_EXTRACT',
        'NORMALIZE', 'CHUNK', 'EMBED', 'INDEX', 'ACTIVATE', 'REINDEX', 'CLEANUP'
    )),
    CONSTRAINT chk_processing_jobs_progress CHECK (
        progress IS NULL OR progress BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_processing_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_processing_jobs_max_attempts CHECK (max_attempts >= 1),
    CONSTRAINT chk_processing_jobs_attempt_limit CHECK (attempt_count <= max_attempts)
);

CREATE UNIQUE INDEX uq_processing_jobs_active_material_version_stage
    ON processing_jobs (material_version_id, job_type, processing_version)
    WHERE material_version_id IS NOT NULL
      AND status IN ('PENDING', 'RUNNING', 'RETRY');
