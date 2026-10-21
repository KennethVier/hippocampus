-- DESIGN ONLY: do not execute or copy to production migrations without approval.
-- Proposed next file in the verified derivative: V28__create_study_mission_start_receipts.sql.
-- Recheck that V28 remains free in that future derivative. No backfill.

CREATE TABLE study_mission_start_receipts (
    owner_id UUID NOT NULL,
    start_request_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    fingerprint_version SMALLINT NOT NULL CHECK (fingerprint_version = 1),
    mission_id UUID NOT NULL UNIQUE,
    objective_id UUID NOT NULL,
    frozen_binding_fingerprint CHAR(64) NOT NULL CHECK (frozen_binding_fingerprint ~ '^[0-9a-f]{64}$'),
    original_action_fingerprint CHAR(64) NOT NULL CHECK (original_action_fingerprint ~ '^[0-9a-f]{64}$'),
    state VARCHAR(24) NOT NULL CHECK (state IN
        ('READY','CLAIMED','RETRYABLE','NO_ACTIVITY','ACTIVITY_CREATED','EXHAUSTED')),
    attempts_used SMALLINT NOT NULL DEFAULT 0 CHECK (attempts_used BETWEEN 0 AND 3),
    generation BIGINT NOT NULL DEFAULT 0 CHECK (generation >= 0),
    claim_token UUID NULL,
    claimed_at TIMESTAMPTZ NULL,
    work_deadline TIMESTAMPTZ NULL,
    lease_expires_at TIMESTAMPTZ NULL,
    claim_snapshot JSONB NULL,
    selected_action JSONB NULL,
    blocking_context_fingerprint CHAR(64) NULL CHECK (blocking_context_fingerprint IS NULL
        OR blocking_context_fingerprint ~ '^[0-9a-f]{64}$'),
    next_retry_at TIMESTAMPTZ NULL,
    accepted_activity_id UUID NULL,
    reason_code VARCHAR(48) NULL CHECK (reason_code IS NULL OR reason_code IN
        ('DEPENDENCY_UNAVAILABLE','PREPARATION_TIMEOUT','INVALID_GENERATED_CONTENT',
         'SOURCE_UNAVAILABLE','SOURCE_BUSY','STATE_CHANGED','MISSION_PAUSED',
         'MISSION_TERMINAL','RETRY_LIMIT_REACHED','POLICY_DECISION_REQUIRED','INITIAL_COMPLETION_NOT_ADMISSIBLE')),
    attempt_history JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    PRIMARY KEY (owner_id, start_request_id),
    FOREIGN KEY (mission_id, owner_id) REFERENCES study_missions(id, user_id) ON DELETE RESTRICT,
    FOREIGN KEY (objective_id, mission_id) REFERENCES learning_objectives(id, study_mission_id) ON DELETE RESTRICT,
    FOREIGN KEY (accepted_activity_id, mission_id) REFERENCES learning_activities(id, study_mission_id) ON DELETE RESTRICT,
    CHECK (owner_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (start_request_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (jsonb_typeof(attempt_history)='array' AND jsonb_array_length(attempt_history)=attempts_used
        AND octet_length(attempt_history::text)<=8192),
    CHECK (claim_snapshot IS NULL OR (jsonb_typeof(claim_snapshot)='object' AND octet_length(claim_snapshot::text)<=4096)),
    CHECK (selected_action IS NULL OR (jsonb_typeof(selected_action)='object' AND octet_length(selected_action::text)<=8192)),
    CHECK ((state='CLAIMED' AND claim_token IS NOT NULL AND claimed_at IS NOT NULL
        AND work_deadline IS NOT NULL AND lease_expires_at IS NOT NULL
        AND claim_snapshot IS NOT NULL AND selected_action IS NOT NULL
        AND attempts_used>0 AND generation>0 AND next_retry_at IS NULL
        AND work_deadline=claimed_at+INTERVAL '150 seconds'
        AND lease_expires_at=claimed_at+INTERVAL '180 seconds')
        OR (state<>'CLAIMED' AND claim_token IS NULL AND claimed_at IS NULL
        AND work_deadline IS NULL AND lease_expires_at IS NULL AND claim_snapshot IS NULL)),
    CHECK ((state='ACTIVITY_CREATED' AND accepted_activity_id IS NOT NULL AND reason_code IS NULL)
        OR (state<>'ACTIVITY_CREATED' AND accepted_activity_id IS NULL)),
    CHECK (state<>'EXHAUSTED' OR (attempts_used=3 AND reason_code='RETRY_LIMIT_REACHED')),
    CHECK (state<>'READY' OR (attempts_used=0 AND generation=0 AND next_retry_at IS NULL)),
    CHECK (next_retry_at IS NULL OR state='RETRYABLE'),
    CHECK (blocking_context_fingerprint IS NULL OR state IN ('NO_ACTIVITY','EXHAUSTED'))
);

-- No sweeper/queue: lookups are by owner/key or mission. No lease-scanning index/job.
-- The receipt itself is immutable after initial activity acceptance.
CREATE FUNCTION guard_study_mission_start_receipt_identity() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE i integer; old_count integer;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Start receipt deletion is not permitted' USING ERRCODE='23514';
    END IF;
    IF ROW(NEW.owner_id,NEW.start_request_id,NEW.request_fingerprint,NEW.fingerprint_version,
        NEW.mission_id,NEW.objective_id,NEW.frozen_binding_fingerprint,
        NEW.original_action_fingerprint,NEW.created_at)
        IS DISTINCT FROM
        ROW(OLD.owner_id,OLD.start_request_id,OLD.request_fingerprint,OLD.fingerprint_version,
        OLD.mission_id,OLD.objective_id,OLD.frozen_binding_fingerprint,
        OLD.original_action_fingerprint,OLD.created_at) THEN
        RAISE EXCEPTION 'Start identity is immutable' USING ERRCODE='23514';
    END IF;
    IF OLD.accepted_activity_id IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Accepted start receipt is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.attempts_used<OLD.attempts_used OR NEW.attempts_used>OLD.attempts_used+1
        OR NEW.generation<OLD.generation OR NEW.updated_at<OLD.updated_at THEN
        RAISE EXCEPTION 'Invalid start receipt transition' USING ERRCODE='23514';
    END IF;
    old_count:=jsonb_array_length(OLD.attempt_history);
    IF old_count>0 THEN
        FOR i IN 0..old_count-1 LOOP
            IF (OLD.attempt_history->i->>'finishedAt') IS NOT NULL THEN
                IF (NEW.attempt_history->i) IS DISTINCT FROM (OLD.attempt_history->i) THEN
                    RAISE EXCEPTION 'Closed start attempt is immutable' USING ERRCODE='23514';
                END IF;
            ELSE
                -- Only the pending entry's close fields may change, including lease expiry.
                IF ((NEW.attempt_history->i)-'finishedAt'-'outcome'-'reasonCode')
                    IS DISTINCT FROM ((OLD.attempt_history->i)-'finishedAt'-'outcome'-'reasonCode') THEN
                    RAISE EXCEPTION 'Start attempt identity is immutable' USING ERRCODE='23514';
                END IF;
            END IF;
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_study_mission_start_receipt
    BEFORE UPDATE OR DELETE ON study_mission_start_receipts
    FOR EACH ROW EXECUTE FUNCTION guard_study_mission_start_receipt_identity();

-- Additional exact application invariants (not replaced by JSON syntax checks):
-- * request/frozen binding fingerprints computed by versioned canonical encoder, never client hashes.
-- * snapshot keys: updatedAt, currentActivityId=null, activityCount=0, maxSequence=0,
--   objectiveId, frozenBindingFingerprint; reject missing/unknown fields.
-- * selected_action: exact typed NextLearningAction, no free-form replacement action.
-- * history entry: generation, startedAt, finishedAt nullable, outcome nullable,
--   reasonCode nullable. Maximum three entries. Only fixed enums/canonical timestamps.
-- * legal claims increment generation and attempts_used by one, append one pending entry;
--   fencing without a new claim may increment generation without consuming another slot.
-- * T3 sets ACTIVITY_CREATED and same-mission first activity atomically with original
--   artifact/contract/activity writes, after owner, source, deadline and snapshot admission.
-- * Every mutation uses mission-first lock order and token/generation compare-and-set.
-- * no SELECT/GET performs an UPDATE; no cleanup/retention deletion is introduced.
-- * trigger/constraints are design proposals; PostgreSQL tests must verify them before acceptance.

-- v2.1: unchanged blocking-context repeats do not UPDATE. The private hash is not
-- a source-authorization capability. EXHAUSTED remains operational RETRY_LIMIT_REACHED;
-- NO_ACTIVITY may separately expose initial completion admission denial, never COMPLETED.
