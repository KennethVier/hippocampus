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
    void followUpHistoryRecoveryPreservesStableLegacyIdentityAndPolicyOrdering() {
        var old=activity(LearningActionType.RETRIEVE);
        var newer=new LearningActivity(UUID.randomUUID(),OBJECTIVE_ID,LearningActivityType.RETRIEVE,LearningActionType.RETRIEVE,
                null,null,"COMPLETED",LearningDifficulty.FOUNDATIONAL,2,null,false,NOW,NOW,NOW,Set.of());
        var base=mission(old);
        var mission=new StudyMission(base.id(),base.userId(),base.topicId(),base.subtopicId(),base.status(),base.learningState(),
                base.groundingMode(),base.availableTimeMinutes(),base.startedAt(),null,null,newer.id(),base.materials(),base.objectives(),
                List.of(old,newer),base.createdAt(),NOW);
        var first=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),USER_ID,old.id(),1,"old",null,NOW,"INCORRECT",null,"INCORRECT",NOW);
        var last=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),USER_ID,newer.id(),1,"new",null,NOW,"CORRECT",null,"CORRECT",NOW);
        var history=Map.of(old.id(),List.of(first),newer.id(),List.of(last));
        var recovered=assembler.assembleForFollowUp(mission,newer,history,NOW);
        var restarted=new StudyMissionLearningStateAssembler().assembleForFollowUp(mission,newer,history,NOW);
        assertThat(recovered.recentActivityHistory()).isEqualTo(restarted.recentActivityHistory());
        assertThat(recovered.recentActivityHistory()).extracting(com.hippocampus.learning.domain.RecentLearningActivity::learningActivityId)
                .containsExactly(old.id(),newer.id());
        assertThat(recovered.recentActivityHistory()).extracting(com.hippocampus.learning.domain.RecentLearningActivity::questionIntent)
                .containsOnly(com.hippocampus.learning.domain.FollowUpIntent.identity(OBJECTIVE_ID,"cardiac-output"));
    }

    @Test
    void bareCorrectUnderstandingEnumContributesNoUnderstandingOrRecall() {
        LearningActivity check = activity(LearningActionType.UNDERSTANDING_CHECK);

        var state = assembler.assembleForSubmission(
                mission(check), check, Map.of(check.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(new UnderstandRetrievePolicy().select(state).actionType())
                .isEqualTo(LearningActionType.UNDERSTAND);
    }

    @Test
    void unqualifiedPartialAndIncorrectChecksHaveNoGradeEvidence() {
        for (AttemptOutcome outcome : List.of(AttemptOutcome.PARTIAL, AttemptOutcome.INCORRECT)) {
            LearningActivity check = activity(LearningActionType.UNDERSTANDING_CHECK);
            var state = assembler.assembleForSubmission(
                    mission(check), check, Map.of(check.id(), List.of()), outcome, NOW);

            assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                    .isEqualTo(EvidenceStrength.INSUFFICIENT);
            assertThat(new UnderstandRetrievePolicy().select(state).actionType())
                    .isEqualTo(LearningActionType.UNDERSTAND);
        }
    }

    @Test
    void bareCorrectRetrieveEnumContributesNoRecall() {
        LearningActivity retrieve = activity(LearningActionType.RETRIEVE);

        var state = assembler.assembleForSubmission(
                mission(retrieve), retrieve, Map.of(retrieve.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
    }

    @Test
    void bareConnectOutcomeDoesNotProveQualification() {
        LearningActivity connect = activity(LearningActionType.CONNECT);

        var state = assembler.assembleForSubmission(
                mission(connect), connect, Map.of(connect.id(), List.of()), AttemptOutcome.CORRECT, NOW);

        assertThat(state.evidence().strengthOf(EvidenceDimension.CONNECTION))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(state.evidence().strengthOf(EvidenceDimension.RECALL))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"UNKNOWN", "correct", " CORRECT ", "CORRUPT"})
    void corruptPersistedEvaluationStatusFailsClosed(String persistedStatus) {
        LearningActivity retrieve = activity(LearningActionType.RETRIEVE);
        var attempt = new com.hippocampus.progress.domain.StudentAttempt(
                UUID.randomUUID(), USER_ID, retrieve.id(), 1, "Saved response", null,
                NOW.minusSeconds(10), persistedStatus, null, null, NOW.minusSeconds(10));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assembler.assembleFromPersistedAttempts(
                mission(retrieve), retrieve, Map.of(retrieve.id(), List.of(attempt)), NOW))
                .isInstanceOf(IllegalArgumentException.class);
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
