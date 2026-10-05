CREATE TABLE evidence_events (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    subtopic_id UUID NULL,
    concept_key VARCHAR NULL,
    student_attempt_id UUID NULL,
    learning_activity_id UUID NOT NULL,
    event_type VARCHAR NOT NULL,
    outcome VARCHAR NOT NULL,
    difficulty VARCHAR NULL,
    confidence VARCHAR NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_evidence_events PRIMARY KEY (id),
    CONSTRAINT fk_evidence_events_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_evidence_events_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT fk_evidence_events_subtopic_same_topic FOREIGN KEY (subtopic_id, topic_id)
        REFERENCES subtopics (id, topic_id) ON DELETE RESTRICT,
    CONSTRAINT fk_evidence_events_student_attempt FOREIGN KEY (student_attempt_id)
        REFERENCES student_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_evidence_events_learning_activity FOREIGN KEY (learning_activity_id)
        REFERENCES learning_activities (id) ON DELETE RESTRICT
);

CREATE INDEX idx_evidence_events_user_topic_occurred_at
    ON evidence_events (user_id, topic_id, occurred_at);

CREATE TABLE learning_evidence (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    subtopic_id UUID NULL,
    concept_key VARCHAR NULL,
    evidence_dimension VARCHAR NOT NULL,
    state VARCHAR NOT NULL,
    supporting_event_count INT NOT NULL DEFAULT 0,
    last_observed_at TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_learning_evidence PRIMARY KEY (id),
    CONSTRAINT fk_learning_evidence_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_evidence_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_evidence_subtopic_same_topic FOREIGN KEY (subtopic_id, topic_id)
        REFERENCES subtopics (id, topic_id) ON DELETE RESTRICT,
    CONSTRAINT chk_learning_evidence_dimension CHECK (
        evidence_dimension IN (
            'RETRIEVAL', 'UNDERSTANDING', 'CONNECTION', 'APPLICATION',
            'VISUAL_IDENTIFICATION', 'REVIEW_RETENTION'
        )
    ),
    CONSTRAINT chk_learning_evidence_state CHECK (
        state IN ('STRONG', 'DEVELOPING', 'WEAK', 'INSUFFICIENT_EVIDENCE')
    ),
    CONSTRAINT chk_learning_evidence_supporting_event_count CHECK (
        supporting_event_count >= 0
    )
);

CREATE INDEX idx_learning_evidence_user_topic_dimension
    ON learning_evidence (user_id, topic_id, evidence_dimension);
