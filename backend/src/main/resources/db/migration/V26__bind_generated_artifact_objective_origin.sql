ALTER TABLE generated_artifacts
    ADD CONSTRAINT uq_generated_artifacts_id_user UNIQUE (id, user_id);

ALTER TABLE study_missions
    ADD CONSTRAINT uq_study_missions_id_user UNIQUE (id, user_id);

CREATE TABLE generated_artifact_objective_origins (
    generated_artifact_id UUID NOT NULL,
    learning_objective_id UUID NOT NULL,
    study_mission_id UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_generated_artifact_objective_origins PRIMARY KEY (generated_artifact_id),
    CONSTRAINT fk_artifact_origin_same_owner FOREIGN KEY (generated_artifact_id, user_id)
        REFERENCES generated_artifacts (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_artifact_origin_mission_same_owner FOREIGN KEY (study_mission_id, user_id)
        REFERENCES study_missions (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_artifact_origin_objective_same_mission
        FOREIGN KEY (learning_objective_id, study_mission_id)
        REFERENCES learning_objectives (id, study_mission_id) ON DELETE RESTRICT
);

CREATE INDEX idx_artifact_origins_objective_mission
    ON generated_artifact_objective_origins (learning_objective_id, study_mission_id);

CREATE FUNCTION reject_artifact_origin_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Generated artifact objective origin is immutable'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_artifact_origin_immutable
    BEFORE UPDATE OR DELETE ON generated_artifact_objective_origins
    FOR EACH ROW EXECUTE FUNCTION reject_artifact_origin_mutation();
