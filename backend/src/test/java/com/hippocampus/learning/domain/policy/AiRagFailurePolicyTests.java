package com.hippocampus.learning.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDependencyFailure;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRagFailurePolicyTests {

    private final AiRagFailurePolicy policy = new AiRagFailurePolicy();

    @Test
    void ragLimitedUsesOnlyAvailableSourceAndRagInsufficientIsExplicit() {
        var limited = policy.handle(state(true, false), LearningDependencyFailure.RAG_LIMITED, failedAction());
        var insufficient = policy.handle(
                state(false, false), LearningDependencyFailure.RAG_INSUFFICIENT, failedAction());

        assertThat(limited.actionType()).isEqualTo(LearningActionType.SOURCE_ONLY);
        assertThat(limited.aiTaskRequired()).isFalse();
        assertThat(insufficient.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
    }

    @Test
    void ragFailureReusesValidatedContentOrReturnsRetryCapableAction() {
        var reused = policy.handle(state(false, true), LearningDependencyFailure.RAG_FAILED, failedAction());
        var retried = policy.handle(state(false, false), LearningDependencyFailure.RAG_FAILED, failedAction());

        assertThat(reused.actionType()).isEqualTo(LearningActionType.REUSE_VALIDATED_CONTENT);
        assertThat(retried.actionType()).isEqualTo(LearningActionType.RETRY_DEPENDENCY);
    }

    @Test
    void aiUnavailableUsesSourceOnlyFallbackOrPausesSafely() {
        var sourceOnly = policy.handle(state(true, false), LearningDependencyFailure.AI_UNAVAILABLE, failedAction());
        var paused = policy.handle(state(false, false), LearningDependencyFailure.AI_UNAVAILABLE, failedAction());

        assertThat(sourceOnly.actionType()).isEqualTo(LearningActionType.SOURCE_ONLY);
        assertThat(paused.actionType()).isEqualTo(LearningActionType.PAUSE);
    }

    @Test
    void evaluationFailureDoesNotCreateOrUpgradeEvidence() {
        LearningState state = state(true, false);
        var action = policy.handle(state, LearningDependencyFailure.AI_EVALUATION_FAILED, failedAction());

        assertThat(action.actionType()).isEqualTo(LearningActionType.RETRY_DEPENDENCY);
        assertThat(state.evidence().strengthOf(EvidenceDimension.APPLICATION))
                .isEqualTo(EvidenceStrength.WEAK);
    }

    private static LearningState state(boolean groundedText, boolean validatedContent) {
        var history = validatedContent
                ? List.of(PolicyTestFixtures.activity(
                        LearningActionType.APPLY,
                        LearningDifficulty.INTERMEDIATE,
                        null,
                        null,
                        null,
                        LearningActivityIntent.STANDARD,
                        true))
                : List.<com.hippocampus.learning.domain.RecentLearningActivity>of();
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                Map.of(EvidenceDimension.APPLICATION, EvidenceStrength.WEAK),
                history,
                false,
                new SourceCapability(SourceReadiness.LIMITED, groundedText, false, false),
                new LearningTimeContext(30, 20, 10),
                strictConstraints());
    }

    private static NextLearningAction failedAction() {
        return new NextLearningAction(
                LearningActionType.APPLY,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.INTERMEDIATE,
                "FAILED_ACTION",
                true,
                strictConstraints());
    }

    private static LearningActionConstraints strictConstraints() {
        return new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                false,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
    }
}
