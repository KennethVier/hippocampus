CREATE TABLE student_attempts (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    learning_activity_id UUID NOT NULL,
    attempt_number INT NOT NULL,
    response_text TEXT NULL,
    response_payload JSONB NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    evaluation_status VARCHAR NOT NULL,
    evaluation_artifact_id UUID NULL,
    deterministic_result VARCHAR NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_student_attempts PRIMARY KEY (id),
    CONSTRAINT uq_student_attempts_activity_number
        UNIQUE (learning_activity_id, attempt_number),
    CONSTRAINT fk_student_attempts_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_attempts_learning_activity FOREIGN KEY (learning_activity_id)
        REFERENCES learning_activities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_attempts_evaluation_artifact FOREIGN KEY (evaluation_artifact_id)
        REFERENCES generated_artifacts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_attempts_attempt_number CHECK (attempt_number >= 1)
);
