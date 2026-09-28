ALTER TABLE subtopics
    ADD CONSTRAINT uq_subtopics_id_topic_id UNIQUE (id, topic_id);

CREATE TABLE generated_artifacts (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    artifact_type VARCHAR NOT NULL,
    task_type VARCHAR NOT NULL,
    content_text TEXT NULL,
    content_payload JSONB NULL,
    grounding_mode VARCHAR NOT NULL,
    classification VARCHAR NOT NULL,
    prompt_id VARCHAR NOT NULL,
    prompt_version VARCHAR NOT NULL,
    provider VARCHAR NOT NULL,
    model VARCHAR NOT NULL,
    model_version VARCHAR NULL,
    validation_status VARCHAR NOT NULL,
    reusable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_generated_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_generated_artifacts_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_generated_artifacts_user_id ON generated_artifacts (user_id);

CREATE TABLE study_missions (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    subtopic_id UUID NULL,
    status VARCHAR NOT NULL,
    learning_state VARCHAR NULL,
    grounding_mode VARCHAR NOT NULL,
    available_time_minutes INT NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    stopped_at TIMESTAMPTZ NULL,
    current_activity_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_study_missions PRIMARY KEY (id),
    CONSTRAINT fk_study_missions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_study_missions_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT fk_study_missions_subtopic_same_topic FOREIGN KEY (subtopic_id, topic_id)
        REFERENCES subtopics (id, topic_id) ON DELETE RESTRICT,
    CONSTRAINT chk_study_missions_status CHECK (
        status IN ('PLANNED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'STOPPED', 'FAILED')
    ),
    CONSTRAINT chk_study_missions_learning_state CHECK (
        learning_state IS NULL OR learning_state IN (
            'UNDERSTANDING', 'PREREQUISITE_SUPPORT', 'RETRIEVAL', 'CONNECTION',
            'APPLICATION', 'FEEDBACK', 'REFLECTION', 'EVIDENCE_UPDATE'
        )
    ),
    CONSTRAINT chk_study_missions_grounding_mode CHECK (
        grounding_mode IN ('STRICT_SOURCE', 'SOURCE_FIRST', 'GENERAL_KNOWLEDGE')
    ),
    CONSTRAINT chk_study_missions_available_time CHECK (
        available_time_minutes IS NULL OR available_time_minutes >= 0
    )
);

CREATE INDEX idx_study_missions_user_id ON study_missions (user_id);
CREATE INDEX idx_study_missions_topic_id ON study_missions (topic_id);
CREATE INDEX idx_study_missions_subtopic_topic
    ON study_missions (subtopic_id, topic_id) WHERE subtopic_id IS NOT NULL;

CREATE TABLE mission_materials (
    id UUID NOT NULL,
    study_mission_id UUID NOT NULL,
    material_id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    document_node_id UUID NULL,
    CONSTRAINT pk_mission_materials PRIMARY KEY (id),
    CONSTRAINT fk_mission_materials_mission FOREIGN KEY (study_mission_id)
        REFERENCES study_missions (id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_materials_material FOREIGN KEY (material_id)
        REFERENCES materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mission_materials_version_same_material
        FOREIGN KEY (material_id, material_version_id)
        REFERENCES material_versions (material_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_mission_materials_node_same_version
        FOREIGN KEY (document_node_id, material_version_id)
        REFERENCES document_nodes (id, material_version_id) ON DELETE RESTRICT,
    CONSTRAINT uq_mission_materials_scope UNIQUE NULLS NOT DISTINCT (
        study_mission_id, material_version_id, document_node_id
    )
);

CREATE INDEX idx_mission_materials_material_version
    ON mission_materials (material_id, material_version_id);
CREATE INDEX idx_mission_materials_document_node_version
    ON mission_materials (document_node_id, material_version_id)
    WHERE document_node_id IS NOT NULL;

CREATE TABLE learning_objectives (
    id UUID NOT NULL,
    study_mission_id UUID NOT NULL,
    objective_text TEXT NOT NULL,
    concept_key VARCHAR NULL,
    display_name VARCHAR NULL,
    priority INT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_learning_objectives PRIMARY KEY (id),
    CONSTRAINT uq_learning_objectives_id_mission UNIQUE (id, study_mission_id),
    CONSTRAINT fk_learning_objectives_mission FOREIGN KEY (study_mission_id)
        REFERENCES study_missions (id) ON DELETE CASCADE,
    CONSTRAINT chk_learning_objectives_status CHECK (
        status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'DEFERRED')
    )
);

CREATE INDEX idx_learning_objectives_mission_id
    ON learning_objectives (study_mission_id);

CREATE TABLE learning_activities (
    id UUID NOT NULL,
    study_mission_id UUID NOT NULL,
    learning_objective_id UUID NULL,
    activity_type VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    difficulty VARCHAR NULL,
    sequence_number INT NOT NULL,
    generated_artifact_id UUID NULL,
    source_required BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_learning_activities PRIMARY KEY (id),
    CONSTRAINT uq_learning_activities_id_mission UNIQUE (id, study_mission_id),
    CONSTRAINT uq_learning_activities_mission_sequence
        UNIQUE (study_mission_id, sequence_number),
    CONSTRAINT fk_learning_activities_mission FOREIGN KEY (study_mission_id)
        REFERENCES study_missions (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_activities_objective_same_mission
        FOREIGN KEY (learning_objective_id, study_mission_id)
        REFERENCES learning_objectives (id, study_mission_id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_activities_generated_artifact FOREIGN KEY (generated_artifact_id)
        REFERENCES generated_artifacts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_learning_activities_type CHECK (
        activity_type IN ('UNDERSTAND', 'RETRIEVE', 'CONNECT', 'APPLY', 'VISUAL', 'FEEDBACK', 'REFLECT')
    ),
    CONSTRAINT chk_learning_activities_difficulty CHECK (
        difficulty IS NULL OR difficulty IN ('FOUNDATIONAL', 'INTERMEDIATE', 'APPLIED')
    ),
    CONSTRAINT chk_learning_activities_sequence CHECK (sequence_number >= 1)
);

CREATE INDEX idx_learning_activities_objective_mission
    ON learning_activities (learning_objective_id, study_mission_id)
    WHERE learning_objective_id IS NOT NULL;
CREATE INDEX idx_learning_activities_generated_artifact
    ON learning_activities (generated_artifact_id)
    WHERE generated_artifact_id IS NOT NULL;

ALTER TABLE study_missions
    ADD CONSTRAINT fk_study_missions_current_activity_same_mission
    FOREIGN KEY (current_activity_id, id)
    REFERENCES learning_activities (id, study_mission_id)
    ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE activity_source_references (
    learning_activity_id UUID NOT NULL,
    source_reference_id UUID NOT NULL,
    CONSTRAINT pk_activity_source_references
        PRIMARY KEY (learning_activity_id, source_reference_id),
    CONSTRAINT fk_activity_source_references_activity FOREIGN KEY (learning_activity_id)
        REFERENCES learning_activities (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_source_references_source FOREIGN KEY (source_reference_id)
        REFERENCES source_references (id) ON DELETE RESTRICT
);

CREATE INDEX idx_activity_source_references_source_id
    ON activity_source_references (source_reference_id);
