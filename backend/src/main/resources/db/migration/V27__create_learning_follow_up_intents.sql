-- V26 is reserved for the separately frozen P7-16 candidate. No backfill.
ALTER TABLE study_missions ADD CONSTRAINT uq_follow_up_mission_owner UNIQUE (id, user_id);
ALTER TABLE learning_activities ADD CONSTRAINT uq_follow_up_activity_objective
    UNIQUE (id, study_mission_id, learning_objective_id);
ALTER TABLE student_attempts ADD CONSTRAINT uq_follow_up_attempt_owner_activity
    UNIQUE (id, user_id, learning_activity_id);

CREATE TABLE learning_follow_up_intents (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    objective_id UUID NOT NULL,
    originating_attempt_id UUID NOT NULL,
    originating_activity_id UUID NOT NULL,
    grade VARCHAR NOT NULL CHECK (grade IN ('CORRECT','PARTIAL','INCORRECT')),
    purpose VARCHAR NOT NULL CHECK (purpose IN ('DEEPEN_OR_TRANSFER','REPAIR_GAP','SCAFFOLD_OR_RETRY')),
    target VARCHAR(4096) NOT NULL CHECK (length(btrim(target)) > 0),
    basis JSONB NOT NULL CHECK (jsonb_typeof(basis)='object' AND octet_length(basis::text)<=150000),
    scope JSONB NOT NULL CHECK (jsonb_typeof(scope)='object' AND octet_length(scope::text)<=250000),
    question_intent VARCHAR(128) NOT NULL,
    state VARCHAR NOT NULL CHECK (state IN ('PENDING','DEFERRED','MATERIALIZED','SATISFIED','CANCELED')),
    linked_activity_id UUID UNIQUE,
    reason VARCHAR NULL CHECK (reason IN ('PREREQUISITE_SUPPORT','TIME_LIMIT','STOP',
        'TERMINAL_COMPLETION','SUPERSEDED','SOURCE_LOSS','SAFETY')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at>=created_at),
    UNIQUE (originating_attempt_id,objective_id),
    FOREIGN KEY (mission_id,owner_id) REFERENCES study_missions(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (objective_id,mission_id) REFERENCES learning_objectives(id,study_mission_id),
    FOREIGN KEY (originating_attempt_id,owner_id,originating_activity_id)
        REFERENCES student_attempts(id,user_id,learning_activity_id),
    FOREIGN KEY (originating_activity_id,mission_id,objective_id)
        REFERENCES learning_activities(id,study_mission_id,learning_objective_id),
    FOREIGN KEY (linked_activity_id,mission_id,objective_id)
        REFERENCES learning_activities(id,study_mission_id,learning_objective_id),
    CHECK ((grade='CORRECT' AND purpose='DEEPEN_OR_TRANSFER') OR
        (grade='PARTIAL' AND purpose='REPAIR_GAP') OR (grade='INCORRECT' AND purpose='SCAFFOLD_OR_RETRY')),
    CHECK ((state IN ('MATERIALIZED','SATISFIED') AND linked_activity_id IS NOT NULL) OR
        (state IN ('PENDING','DEFERRED') AND linked_activity_id IS NULL) OR state='CANCELED'),
    CHECK ((state='DEFERRED' AND reason IS NOT NULL AND reason IN ('PREREQUISITE_SUPPORT','TIME_LIMIT')) OR
        (state='CANCELED' AND reason IS NOT NULL AND reason IN ('STOP','TERMINAL_COMPLETION','SUPERSEDED','SOURCE_LOSS','SAFETY')) OR
        (state IN ('PENDING','MATERIALIZED','SATISFIED') AND reason IS NULL))
);
CREATE INDEX idx_follow_up_owned_mission ON learning_follow_up_intents(owner_id,mission_id);
