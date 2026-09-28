package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.READY_FOR_RETRIEVAL;
import static com.hippocampus.learning.domain.LearningRationaleCodes.RETRIEVAL_GAP;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnderstandRetrievePolicyTests {

    private final UnderstandRetrievePolicy policy = new UnderstandRetrievePolicy();

    @Test
    void selectsUnderstandingForInsufficientOrWeakFoundation() {
        for (EvidenceStrength strength : List.of(EvidenceStrength.INSUFFICIENT, EvidenceStrength.WEAK)) {
            var action = policy.select(PolicyTestFixtures.state(Map.of(EvidenceDimension.UNDERSTANDING, strength)));

            assertThat(action.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
            assertThat(action.rationaleCode()).isEqualTo(FOUNDATION_INSUFFICIENT);
        }
    }

    @Test
    void prefersActiveRetrievalOnceFoundationIsDevelopingOrStrong() {
        for (EvidenceStrength strength : List.of(EvidenceStrength.DEVELOPING, EvidenceStrength.STRONG)) {
            var action = policy.select(PolicyTestFixtures.state(Map.of(EvidenceDimension.UNDERSTANDING, strength)));

            assertThat(action.actionType()).isEqualTo(LearningActionType.RETRIEVE);
            assertThat(action.rationaleCode()).isEqualTo(READY_FOR_RETRIEVAL);
        }
    }

    @Test
    void routesRetrievalFailureBackToTargetedUnderstanding() {
        LearningState state = PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                Map.of(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING),
                List.of(PolicyTestFixtures.activity(
                        LearningActionType.RETRIEVE, LearningDifficulty.FOUNDATIONAL, AttemptOutcome.INCORRECT)),
                false,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(15, 15, 0),
                LearningActionConstraints.unconstrained());

        var action = policy.select(state);

        assertThat(action.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(action.rationaleCode()).isEqualTo(RETRIEVAL_GAP);
    }
}
