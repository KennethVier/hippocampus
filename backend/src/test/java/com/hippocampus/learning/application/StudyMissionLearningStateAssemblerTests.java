package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.policy.UnderstandRetrievePolicy;

class StudyMissionLearningStateAssemblerTests {

    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();

    private final StudyMissionLearningStateAssembler assembler =
            new StudyMissionLearningStateAssembler();

    @Test
    void correctUnderstandingCheckContributesDevelopingUnderstandingAndNoRecall() {
        LearningActivity check = activity(LearningActionType.UNDERSTANDING_CHECK);

        var state = assembler.assembleForSubmission(
                mission(check), check, Map.of(check.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                .isEqualTo(EvidenceStrength.DEVELOPING);
        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(new UnderstandRetrievePolicy().select(state).actionType())
                .isEqualTo(LearningActionType.RETRIEVE);
    }

    @Test
    void partialAndIncorrectUnderstandingChecksDoNotAdvanceToRetrieval() {
        for (AttemptOutcome outcome : List.of(AttemptOutcome.PARTIAL, AttemptOutcome.INCORRECT)) {
            LearningActivity check = activity(LearningActionType.UNDERSTANDING_CHECK);
            var state = assembler.assembleForSubmission(
                    mission(check), check, Map.of(check.id(), List.of()), outcome, NOW);

            assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                    .isEqualTo(outcome == AttemptOutcome.PARTIAL
                            ? EvidenceStrength.WEAK : EvidenceStrength.INSUFFICIENT);
            assertThat(new UnderstandRetrievePolicy().select(state).actionType())
                    .isEqualTo(LearningActionType.UNDERSTAND);
        }
    }

    @Test
    void normalRetrieveStillContributesRecallOnly() {
        LearningActivity retrieve = activity(LearningActionType.RETRIEVE);

        var state = assembler.assembleForSubmission(
                mission(retrieve), retrieve, Map.of(retrieve.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.DEVELOPING);
        assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
    }

    @Test
    void validatedConnectAttemptContributesConnectionEvidenceOnly() {
        LearningActivity connect = activity(LearningActionType.CONNECT);

        var state = assembler.assembleForSubmission(
                mission(connect), connect, Map.of(connect.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.CONNECTION))
                .isEqualTo(EvidenceStrength.DEVELOPING);
        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
    }

    private static LearningActivity activity(LearningActionType representedAction) {
        LearningActivityType activityType = representedAction == LearningActionType.CONNECT
                ? LearningActivityType.CONNECT
                : LearningActivityType.RETRIEVE;
        return new LearningActivity(
                UUID.randomUUID(), OBJECTIVE_ID, activityType,
                representedAction, null, null, "PENDING", LearningDifficulty.FOUNDATIONAL,
                1, UUID.randomUUID(), true, NOW.minusSeconds(30), null,
                NOW.minusSeconds(60), Set.of());
    }

    private static StudyMission mission(LearningActivity activity) {
        return new StudyMission(
                MISSION_ID, USER_ID, UUID.randomUUID(), null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, activity.id(), List.of(),
                List.of(new LearningObjective(
                        OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                        1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))),
                List.of(activity), NOW.minusSeconds(300), NOW.minusSeconds(30));
    }
}
