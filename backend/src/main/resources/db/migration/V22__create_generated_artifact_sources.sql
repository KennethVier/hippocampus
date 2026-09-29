CREATE TABLE generated_artifact_sources (
    generated_artifact_id UUID NOT NULL,
    source_reference_id UUID NOT NULL,
    CONSTRAINT pk_generated_artifact_sources
        PRIMARY KEY (generated_artifact_id, source_reference_id),
    CONSTRAINT fk_generated_artifact_sources_artifact FOREIGN KEY (generated_artifact_id)
        REFERENCES generated_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_artifact_sources_source FOREIGN KEY (source_reference_id)
        REFERENCES source_references (id) ON DELETE RESTRICT
);

CREATE INDEX idx_generated_artifact_sources_source_id
    ON generated_artifact_sources (source_reference_id);
