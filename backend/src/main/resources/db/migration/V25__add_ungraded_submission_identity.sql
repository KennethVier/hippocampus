ALTER TABLE student_attempts
    ADD COLUMN request_id UUID NULL,
    ADD COLUMN limitation_code VARCHAR NULL,
    ADD COLUMN submission_receipt JSONB NULL;

CREATE UNIQUE INDEX uq_student_attempts_activity_request
    ON student_attempts (learning_activity_id, request_id)
    WHERE request_id IS NOT NULL;

ALTER TABLE student_attempts
    ADD CONSTRAINT chk_student_attempts_ungraded_shape CHECK (
        evaluation_status <> 'UNGRADED'
        OR (deterministic_result IS NULL AND limitation_code IS NOT NULL)
    );
