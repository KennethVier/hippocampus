package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_GAP;
import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_READY;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionPolicyTests {

    private final ConnectionPolicy policy = new ConnectionPolicy();

    @Test
    void weakFoundationsBlockConnectionAndIrrelevantConnectionsAreNotInvented() {
        var insufficient = state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.WEAK,
                EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING), true);
        var irrelevant = state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                EvidenceDimension.RECALL, EvidenceStrength.STRONG), false);

        assertThat(policy.select(insufficient).orElseThrow().actionType())
                .isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(policy.select(irrelevant)).isEmpty();
    }

    @Test
    void selectsPurposefulConnectionAndRoutesConnectionWeakness() {
        var eligible = policy.select(state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING,
                EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING), true)).orElseThrow();
        var weak = policy.select(state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                EvidenceDimension.CONNECTION, EvidenceStrength.WEAK), true)).orElseThrow();

        assertThat(eligible.actionType()).isEqualTo(LearningActionType.CONNECT);
        assertThat(eligible.rationaleCode()).isEqualTo(CONNECTION_READY);
        assertThat(weak.actionType()).isEqualTo(LearningActionType.CONNECT);
        assertThat(weak.rationaleCode()).isEqualTo(CONNECTION_GAP);
    }

    @Test
    void progressesTowardApplicationAfterAdequateConnectionEvidence() {
        var action = policy.select(state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                EvidenceDimension.CONNECTION, EvidenceStrength.DEVELOPING), true)).orElseThrow();

        assertThat(action.actionType()).isEqualTo(LearningActionType.APPLY);
    }

    private static com.hippocampus.learning.domain.LearningState state(
            Map<EvidenceDimension, EvidenceStrength> evidence, boolean relevant) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.CONNECTION,
                evidence,
                List.of(),
                relevant,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained());
    }
}
