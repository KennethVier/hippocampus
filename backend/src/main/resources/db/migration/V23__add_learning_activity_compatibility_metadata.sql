ALTER TABLE learning_activities
    ADD COLUMN represented_action_type VARCHAR NULL,
    ADD COLUMN question_intent VARCHAR NULL,
    ADD COLUMN template_signature VARCHAR NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM learning_activities
        WHERE activity_type = 'VISUAL'
    ) THEN
        RAISE EXCEPTION
            'V23 cannot safely reconstruct represented pedagogical action for legacy VISUAL learning activities';
    END IF;
END
$$;

UPDATE learning_activities
SET represented_action_type = activity_type;

ALTER TABLE learning_activities
    ALTER COLUMN represented_action_type SET NOT NULL,
    ADD CONSTRAINT chk_learning_activities_represented_action_type CHECK (
        represented_action_type IN (
            'UNDERSTAND', 'RETRIEVE', 'CONNECT', 'APPLY', 'HINT',
            'PREREQUISITE_SUPPORT', 'FEEDBACK', 'REFLECT'
        )
    );
