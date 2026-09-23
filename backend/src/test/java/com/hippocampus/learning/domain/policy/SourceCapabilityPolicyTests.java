package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNRELIABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
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

class SourceCapabilityPolicyTests {

    private final SourceCapabilityPolicy policy = new SourceCapabilityPolicy();

    @Test
    void readySourceAllowsGroundedAndReliableVisualAction() {
        var action = policy.adjust(
                state(new SourceCapability(SourceReadiness.READY, true, true, true), strict(true, false)),
                candidate(strict(true, false)));

        assertThat(action.actionType()).isEqualTo(LearningActionType.APPLY);
        assertThat(action.rationaleCode()).isEqualTo("CANDIDATE");
    }

    @Test
    void limitedSourceRemainsExplicitWhileUsingAvailableEvidence() {
        var action = policy.adjust(
                state(new SourceCapability(SourceReadiness.LIMITED, true, false, false), strict(false, false)),
                candidate(strict(false, false)));

        assertThat(action.actionType()).isEqualTo(LearningActionType.APPLY);
        assertThat(action.rationaleCode()).isEqualTo(SOURCE_LIMITED);
    }

    @Test
    void insufficientOrFailedSourceNeverBecomesGroundedSuccess() {
        for (SourceReadiness readiness : List.of(SourceReadiness.INSUFFICIENT, SourceReadiness.FAILED)) {
            LearningActionConstraints constraints = strict(false, false);
            var action = policy.adjust(
                    state(new SourceCapability(readiness, false, false, false), constraints),
                    candidate(constraints));

            assertThat(action.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
            assertThat(action.rationaleCode()).isEqualTo(SOURCE_UNAVAILABLE);
        }
    }

    @Test
    void unavailableOrUnreliableVisualUsesSafeExplicitAlternative() {
        LearningActionConstraints constraints = strict(true, true);
        var unavailable = policy.adjust(
                state(new SourceCapability(SourceReadiness.READY, true, false, false), constraints),
                candidate(constraints));
        var unreliable = policy.adjust(
                state(new SourceCapability(SourceReadiness.READY, true, true, false), constraints),
                candidate(constraints));

        assertThat(unavailable.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(unavailable.rationaleCode()).isEqualTo(VISUAL_UNAVAILABLE);
        assertThat(unavailable.constraints().sourceRequirement()).isEqualTo(SourceRequirement.NONE);
        assertThat(unreliable.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(unreliable.rationaleCode()).isEqualTo(VISUAL_UNRELIABLE);
    }

    private static LearningActionConstraints strict(boolean visual, boolean supplementalAllowed) {
        return new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                visual,
                supplementalAllowed,
                null,
                null,
                LearningActivityIntent.STANDARD);
    }

    private static com.hippocampus.learning.domain.LearningState state(
            SourceCapability source, LearningActionConstraints constraints) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG),
                List.of(),
                false,
                source,
                new LearningTimeContext(30, 30, 0),
                constraints);
    }

    private static NextLearningAction candidate(LearningActionConstraints constraints) {
        return new NextLearningAction(
                LearningActionType.APPLY,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.FOUNDATIONAL,
                "CANDIDATE",
                true,
                constraints);
    }
}
