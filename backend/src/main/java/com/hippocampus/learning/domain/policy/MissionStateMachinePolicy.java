package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.MissionLifecycleState;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MissionStateMachinePolicy {

    private static final Map<MissionLifecycleState, Set<MissionLifecycleState>> ALLOWED_TRANSITIONS =
            allowedTransitions();

    public MissionLifecycleState transition(
            MissionLifecycleState currentState, MissionLifecycleState requestedState) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        Objects.requireNonNull(requestedState, "requestedState must not be null");
        if (!canTransition(currentState, requestedState)) {
            throw new IllegalStateException(
                    "unsupported mission transition: " + currentState + " -> " + requestedState);
        }
        return requestedState;
    }

    public boolean canTransition(
            MissionLifecycleState currentState, MissionLifecycleState requestedState) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        Objects.requireNonNull(requestedState, "requestedState must not be null");
        return ALLOWED_TRANSITIONS.get(currentState).contains(requestedState);
    }

    private static Map<MissionLifecycleState, Set<MissionLifecycleState>> allowedTransitions() {
        EnumMap<MissionLifecycleState, Set<MissionLifecycleState>> transitions =
                new EnumMap<>(MissionLifecycleState.class);
        transitions.put(MissionLifecycleState.PLANNED, EnumSet.of(MissionLifecycleState.ACTIVE));
        transitions.put(
                MissionLifecycleState.ACTIVE,
                EnumSet.of(
                        MissionLifecycleState.PAUSED,
                        MissionLifecycleState.COMPLETED,
                        MissionLifecycleState.STOPPED));
        transitions.put(
                MissionLifecycleState.PAUSED,
                EnumSet.of(MissionLifecycleState.ACTIVE, MissionLifecycleState.STOPPED));
        transitions.put(MissionLifecycleState.COMPLETED, EnumSet.noneOf(MissionLifecycleState.class));
        transitions.put(MissionLifecycleState.STOPPED, EnumSet.noneOf(MissionLifecycleState.class));
        return Map.copyOf(transitions);
    }
}
