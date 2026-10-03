ALTER TABLE learning_activities
    DROP CONSTRAINT chk_learning_activities_represented_action_type;

ALTER TABLE learning_activities
    ADD CONSTRAINT chk_learning_activities_represented_action_type CHECK (
        represented_action_type IN (
            'UNDERSTAND', 'UNDERSTANDING_CHECK', 'RETRIEVE', 'CONNECT', 'APPLY',
            'HINT', 'PREREQUISITE_SUPPORT', 'FEEDBACK', 'REFLECT'
        )
    );
