package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningEvidenceSnapshot;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PolicyTestFixtures {

    private PolicyTestFixtures() {
    }

    static LearningPolicyConfiguration configuration() {
        EnumMap<LearningActionType, Integer> durations = new EnumMap<>(LearningActionType.class);
        for (LearningActionType actionType : LearningActionType.values()) {
            durations.put(actionType, 2);
        }
        durations.put(LearningActionType.UNDERSTAND, 8);
        durations.put(LearningActionType.RETRIEVE, 4);
        durations.put(LearningActionType.CONNECT, 8);
        durations.put(LearningActionType.APPLY, 16);
        durations.put(LearningActionType.REFLECT, 2);
        return new LearningPolicyConfiguration(2, 3, 4, 5, 2, 3, durations);
    }

    static LearningState state(Map<EvidenceDimension, EvidenceStrength> evidence) {
        return state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence,
                List.of(),
                false,
                new SourceCapability(SourceReadiness.READY, true, true, true),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained());
    }

    static LearningState state(
            MissionLifecycleState lifecycle,
            LearningStage stage,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            List<RecentLearningActivity> history,
            boolean connectionRelevant,
            SourceCapability source,
            LearningTimeContext time,
            LearningActionConstraints constraints) {
        return new LearningState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                lifecycle,
                stage,
                new LearningEvidenceSnapshot(evidence),
                source,
                time,
                history,
                connectionRelevant,
                constraints);
    }

    static RecentLearningActivity activity(
            LearningActionType type,
            LearningDifficulty difficulty,
            AttemptOutcome outcome) {
        return activity(type, difficulty, outcome, null, null, LearningActivityIntent.STANDARD, false);
    }

    static RecentLearningActivity activity(
            LearningActionType type,
            LearningDifficulty difficulty,
            AttemptOutcome outcome,
            String questionIntent,
            String templateSignature,
            LearningActivityIntent repetitionIntent,
            boolean validatedContent) {
        return new RecentLearningActivity(
                "cardiac-output",
                type.name(),
                questionIntent,
                difficulty,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                templateSignature,
                outcome,
                repetitionIntent,
                validatedContent);
    }
}
