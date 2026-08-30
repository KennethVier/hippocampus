CREATE TABLE subjects (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR NOT NULL,
    description TEXT NULL,
    sort_order INT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_subjects PRIMARY KEY (id),
    CONSTRAINT fk_subjects_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_subjects_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_subjects_user_lower_name
    ON subjects (user_id, lower(name));

CREATE TABLE topics (
    id UUID NOT NULL,
    subject_id UUID NOT NULL,
    name VARCHAR NOT NULL,
    description TEXT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_topics PRIMARY KEY (id),
    CONSTRAINT fk_topics_subject FOREIGN KEY (subject_id)
        REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT chk_topics_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_topics_subject_id ON topics (subject_id);

CREATE TABLE subtopics (
    id UUID NOT NULL,
    topic_id UUID NOT NULL,
    name VARCHAR NOT NULL,
    description TEXT NULL,
    sort_order INT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_subtopics PRIMARY KEY (id),
    CONSTRAINT fk_subtopics_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT chk_subtopics_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_subtopics_topic_id ON subtopics (topic_id);
