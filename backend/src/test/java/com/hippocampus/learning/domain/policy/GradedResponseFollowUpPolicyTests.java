package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GradedResponseFollowUpPolicyTests {
    final Instant now=Instant.parse("2026-10-18T00:00:00Z");
    final UUID owner=UUID.randomUUID(),mission=UUID.randomUUID(),objective=UUID.randomUUID();
    final GradedResponseFollowUpPolicy policy=new GradedResponseFollowUpPolicy();
    LearningObjective objective(String target) {
        return new LearningObjective(objective,"Explain the relationship",target,null,1,LearningObjectiveStatus.ACTIVE,now);
    }
    FollowUpIntent create(AttemptOutcome grade) {
        return policy.create(UUID.randomUUID(),owner,mission,UUID.randomUUID(),UUID.randomUUID(),objective("validated target"),grade,
                new FollowUpIntent.Basis(grade==AttemptOutcome.PARTIAL ? "VERIFIED_GAP":"DETERMINISTIC_MCQ",
                        grade==AttemptOutcome.PARTIAL ? List.of("verified structured gap"):List.of()),
                new FollowUpIntent.Scope(List.of(),Set.of(),StudyMissionGroundingMode.GENERAL_KNOWLEDGE),now).orElseThrow();
    }
    @Test void choosesGradeSpecificPurposesWithExplicitlySyntheticPartialBasis() {
        assertThat(create(AttemptOutcome.CORRECT).purpose()).isEqualTo(FollowUpIntent.Purpose.DEEPEN_OR_TRANSFER);
        assertThat(create(AttemptOutcome.PARTIAL).purpose()).isEqualTo(FollowUpIntent.Purpose.REPAIR_GAP);
        assertThat(create(AttemptOutcome.INCORRECT).purpose()).isEqualTo(FollowUpIntent.Purpose.SCAFFOLD_OR_RETRY);
    }
    @Test void ungradedCannotCreateAnIntentOrInventABasis() {
        assertThat(policy.create(UUID.randomUUID(),owner,mission,UUID.randomUUID(),UUID.randomUUID(),objective("target"),
                AttemptOutcome.UNGRADED,null,null,now)).isEmpty();
        assertThatThrownBy(() -> policy.create(UUID.randomUUID(),owner,mission,UUID.randomUUID(),UUID.randomUUID(),objective("target"),
                AttemptOutcome.PARTIAL,null,null,now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void identityIgnoresAttemptWordingAndTimeButDistinguishesValidatedTargets() {
        assertThat(create(AttemptOutcome.CORRECT).questionIntent()).isEqualTo(create(AttemptOutcome.CORRECT).questionIntent());
        assertThat(FollowUpIntent.identity(objective,"target A")).isNotEqualTo(FollowUpIntent.identity(objective,"target B"));
        assertThat(FollowUpIntent.identity(UUID.randomUUID(),"target A")).isNotEqualTo(FollowUpIntent.identity(objective,"target A"));
        assertThatThrownBy(() -> FollowUpIntent.identity(objective," ")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deferredIntentSurvivesRestartAndCannotRelinkOrResurrect() {
        FollowUpIntent f=create(AttemptOutcome.INCORRECT);
        FollowUpIntent deferred=f.transition(FollowUpIntent.State.DEFERRED,null,FollowUpIntent.Reason.PREREQUISITE_SUPPORT,now);
        UUID activity=UUID.randomUUID();
        FollowUpIntent linked=deferred.transition(FollowUpIntent.State.MATERIALIZED,activity,null,now);
        assertThat(linked.questionIntent()).isEqualTo(f.questionIntent());
        assertThatThrownBy(() -> linked.transition(FollowUpIntent.State.MATERIALIZED,UUID.randomUUID(),null,now))
                .isInstanceOf(IllegalStateException.class);
        FollowUpIntent satisfied=linked.transition(FollowUpIntent.State.SATISFIED,activity,null,now);
        assertThatThrownBy(() -> satisfied.transition(FollowUpIntent.State.PENDING,null,null,now)).isInstanceOf(IllegalStateException.class);
        FollowUpIntent canceled=linked.transition(FollowUpIntent.State.CANCELED,activity,FollowUpIntent.Reason.SOURCE_LOSS,now);
        assertThatThrownBy(() -> canceled.transition(FollowUpIntent.State.PENDING,null,null,now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void everyAuthorizedCancellationIsTerminalIncludingUnansweredMaterialization() {
        for (var reason:List.of(FollowUpIntent.Reason.STOP,FollowUpIntent.Reason.TERMINAL_COMPLETION,
                FollowUpIntent.Reason.SOURCE_LOSS,FollowUpIntent.Reason.SUPERSEDED,FollowUpIntent.Reason.SAFETY)) {
            var pending=create(AttemptOutcome.INCORRECT);
            var linked=pending.transition(FollowUpIntent.State.MATERIALIZED,UUID.randomUUID(),null,now);
            var canceled=linked.transition(FollowUpIntent.State.CANCELED,linked.linkedActivityId(),reason,now);
            assertThat(canceled.terminal()).isTrue();
            assertThatThrownBy(() -> canceled.transition(FollowUpIntent.State.SATISFIED,canceled.linkedActivityId(),null,now))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void completedSupportCannotBypassAnUnsatisfiedUnderstandingPrerequisite() {
        var f=create(AttemptOutcome.INCORRECT);
        var support=new RecentLearningActivity(UUID.randomUUID(),f.target(),"HINT",null,LearningDifficulty.FOUNDATIONAL,
                mission,null,null,LearningActivityIntent.STANDARD,true);
        var state=new LearningState(mission,objective,f.target(),MissionLifecycleState.ACTIVE,LearningStage.UNDERSTANDING,
                new LearningEvidenceSnapshot(Map.of()),new SourceCapability(SourceReadiness.READY,true,false,false),
                new LearningTimeContext(30,30,0),List.of(support),false,LearningActionConstraints.unconstrained());
        var normal=new NextLearningAction(LearningActionType.UNDERSTAND,objective,f.target(),LearningDifficulty.FOUNDATIONAL,"RETRIEVAL_GAP",true);
        var next=policy.select(state,normal,f);
        assertThat(next).isEqualTo(normal);
    }

    @Test void exactAndParaphrasedTargetsAreSuppressedAcrossDifficultyButIntentionalReassessmentIsAllowed() {
        FollowUpIntent intent=create(AttemptOutcome.CORRECT);
        var history=List.of(new RecentLearningActivity(UUID.randomUUID(),intent.target(),"RETRIEVE",intent.questionIntent(),
                LearningDifficulty.FOUNDATIONAL,mission,null,AttemptOutcome.CORRECT,LearningActivityIntent.STANDARD,true));
        var state=new LearningState(mission,objective,intent.target(),MissionLifecycleState.ACTIVE,LearningStage.RETRIEVAL,
                new LearningEvidenceSnapshot(Map.of()),new SourceCapability(SourceReadiness.READY,true,false,false),
                new LearningTimeContext(30,30,0),history,false,LearningActionConstraints.unconstrained());
        var normal=new NextLearningAction(LearningActionType.REFLECT,objective,intent.target(),null,"TEST",false);
        var candidate=policy.select(state,normal,intent);
        EnumMap<LearningActionType,Integer> durations=new EnumMap<>(LearningActionType.class);
        for(var t:LearningActionType.values()) durations.put(t,1);
        var anti=new AntiRepetitionPolicy(new LearningPolicyConfiguration(2,3,4,5,2,
                LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,durations));
        assertThat(anti.adjust(state,candidate).actionType()).isEqualTo(LearningActionType.REFLECT);
        var corrective=policy.select(state,normal,create(AttemptOutcome.INCORRECT));
        assertThat(anti.adjust(state,corrective)).isEqualTo(corrective);
    }
}
