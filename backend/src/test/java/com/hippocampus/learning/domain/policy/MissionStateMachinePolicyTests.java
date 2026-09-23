package com.hippocampus.learning.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.learning.domain.MissionLifecycleState;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissionStateMachinePolicyTests {

    private final MissionStateMachinePolicy policy = new MissionStateMachinePolicy();

    @Test
    void enumeratesEveryAllowedAndRejectedTransition() {
        Set<String> allowed = Set.of(
                "PLANNED->ACTIVE",
                "ACTIVE->PAUSED",
                "ACTIVE->COMPLETED",
                "ACTIVE->STOPPED",
                "PAUSED->ACTIVE",
                "PAUSED->STOPPED");

        for (MissionLifecycleState from : MissionLifecycleState.values()) {
            for (MissionLifecycleState to : MissionLifecycleState.values()) {
                String transition = from + "->" + to;
                assertThat(policy.canTransition(from, to)).as(transition).isEqualTo(allowed.contains(transition));
                if (allowed.contains(transition)) {
                    assertThat(policy.transition(from, to)).as(transition).isEqualTo(to);
                } else {
                    assertThatThrownBy(() -> policy.transition(from, to))
                            .as(transition)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(transition.replace("->", " -> "));
                }
            }
        }
    }

    @Test
    void completedAndStoppedStatesAreTerminalAndCompletionDoesNotMutateEvidence() {
        assertThat(MissionLifecycleState.COMPLETED).isNotEqualTo(MissionLifecycleState.STOPPED);
        assertThat(MissionLifecycleState.values())
                .allSatisfy(target -> {
                    assertThat(policy.canTransition(MissionLifecycleState.COMPLETED, target)).isFalse();
                    assertThat(policy.canTransition(MissionLifecycleState.STOPPED, target)).isFalse();
                });
    }
}
