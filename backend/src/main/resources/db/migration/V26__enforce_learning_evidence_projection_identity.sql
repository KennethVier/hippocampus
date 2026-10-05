ALTER TABLE learning_evidence
    ADD CONSTRAINT uq_learning_evidence_projection_key
    UNIQUE NULLS NOT DISTINCT (
        user_id,
        topic_id,
        subtopic_id,
        concept_key,
        evidence_dimension
    );
