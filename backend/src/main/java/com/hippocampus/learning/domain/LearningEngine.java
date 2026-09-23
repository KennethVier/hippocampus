package com.hippocampus.learning.domain;

import static com.hippocampus.learning.domain.LearningRationaleCodes.FEEDBACK_DUE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.MISSION_PAUSED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.MISSION_PLANNED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.MISSION_TERMINAL;
import static com.hippocampus.learning.domain.LearningRationaleCodes.OBJECTIVE_COMPLETE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.PREREQUISITE_SUPPORT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.REFLECTION_DUE;

import com.hippocampus.learning.domain.policy.AiRagFailurePolicy;
import com.hippocampus.learning.domain.policy.AntiRepetitionPolicy;
import com.hippocampus.learning.domain.policy.ApplicationPolicy;
import com.hippocampus.learning.domain.policy.ConnectionPolicy;
import com.hippocampus.learning.domain.policy.ScaffoldingPolicy;
import com.hippocampus.learning.domain.policy.SourceCapabilityPolicy;
import com.hippocampus.learning.domain.policy.TimeAwareMissionPolicy;
import com.hippocampus.learning.domain.policy.UnderstandRetrievePolicy;
import java.util.Objects;

public final class LearningEngine {

    private final UnderstandRetrievePolicy understandRetrievePolicy;
    private final ConnectionPolicy connectionPolicy;
    private final ApplicationPolicy applicationPolicy;
    private final ScaffoldingPolicy scaffoldingPolicy;
    private final SourceCapabilityPolicy sourceCapabilityPolicy;
    private final TimeAwareMissionPolicy timeAwareMissionPolicy;
    private final AntiRepetitionPolicy antiRepetitionPolicy;
    private final AiRagFailurePolicy aiRagFailurePolicy;

    public LearningEngine(LearningPolicyConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.understandRetrievePolicy = new UnderstandRetrievePolicy();
        this.connectionPolicy = new ConnectionPolicy();
        this.applicationPolicy = new ApplicationPolicy(configuration);
        this.scaffoldingPolicy = new ScaffoldingPolicy(configuration);
        this.sourceCapabilityPolicy = new SourceCapabilityPolicy();
        this.timeAwareMissionPolicy = new TimeAwareMissionPolicy(configuration);
        this.antiRepetitionPolicy = new AntiRepetitionPolicy(configuration);
        this.aiRagFailurePolicy = new AiRagFailurePolicy();
    }

    public NextLearningAction decide(LearningState state) {
        Objects.requireNonNull(state, "state must not be null");
        NextLearningAction lifecycleAction = lifecycleAction(state);
        if (lifecycleAction != null) {
            return lifecycleAction;
        }

        NextLearningAction candidate = progressionAction(state);
        if (candidate.actionType() == LearningActionType.APPLY
                || state.currentStage() != LearningStage.APPLICATION) {
            candidate = scaffoldingPolicy.select(state).orElse(candidate);
        }
        NextLearningAction sourceSafe = sourceCapabilityPolicy.adjust(state, candidate);
        NextLearningAction timeSafe = timeAwareMissionPolicy.adjust(state, sourceSafe);
        return antiRepetitionPolicy.adjust(state, timeSafe);
    }

    public NextLearningAction handleFailure(
            LearningState state,
            LearningDependencyFailure failure,
            NextLearningAction failedAction) {
        Objects.requireNonNull(state, "state must not be null");
        NextLearningAction lifecycleAction = lifecycleAction(state);
        if (lifecycleAction != null) {
            return lifecycleAction;
        }
        return sourceCapabilityPolicy.adjust(state, aiRagFailurePolicy.handle(state, failure, failedAction));
    }

    private NextLearningAction progressionAction(LearningState state) {
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)
                || !state.evidence().isAtLeast(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING)) {
            return understandRetrievePolicy.select(state);
        }
        return switch (state.currentStage()) {
            case UNDERSTANDING, RETRIEVAL -> state.connectionRelevant()
                    ? connectionPolicy.select(state).orElseGet(() -> applicationPolicy.select(state))
                    : applicationPolicy.select(state);
            case CONNECTION -> connectionPolicy.select(state).orElseGet(() -> applicationPolicy.select(state));
            case APPLICATION -> applicationPolicy.select(state);
            case PREREQUISITE_SUPPORT -> action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    PREREQUISITE_SUPPORT,
                    true);
            case FEEDBACK -> action(state, LearningActionType.FEEDBACK, null, FEEDBACK_DUE, false);
            case REFLECTION -> action(state, LearningActionType.REFLECT, null, REFLECTION_DUE, false);
            case EVIDENCE_UPDATE -> action(state, LearningActionType.COMPLETE, null, OBJECTIVE_COMPLETE, false);
        };
    }

    private static NextLearningAction lifecycleAction(LearningState state) {
        return switch (state.missionState()) {
            case PLANNED -> action(state, LearningActionType.START, null, MISSION_PLANNED, false);
            case PAUSED -> action(state, LearningActionType.RESUME, null, MISSION_PAUSED, false);
            case COMPLETED -> action(state, LearningActionType.COMPLETE, null, MISSION_TERMINAL, false);
            case STOPPED -> action(state, LearningActionType.STOP, null, MISSION_TERMINAL, false);
            case ACTIVE -> null;
        };
    }

    private static NextLearningAction action(
            LearningState state,
            LearningActionType actionType,
            LearningDifficulty difficulty,
            String rationale,
            boolean aiTaskRequired) {
        return new NextLearningAction(
                actionType,
                state.learningObjectiveId(),
                state.conceptKey(),
                difficulty,
                rationale,
                aiTaskRequired,
                state.actionConstraints());
    }
}
