package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

class P716ContinuationTests {
    private final UUID owner = UUID.randomUUID(), missionId = UUID.randomUUID(), activityId = UUID.randomUUID(),
            objectiveId = UUID.randomUUID(), source = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CurrentUser user = () -> new AuthenticatedUser(owner);
    private final StudyMissionRepository missions = mock(StudyMissionRepository.class);
    private final StudentAttemptRepository attempts = mock(StudentAttemptRepository.class);
    private final ActivityResponseContractRepository contracts = mock(ActivityResponseContractRepository.class);
    private final StudyMissionSourcePresentationRepository sources = mock(StudyMissionSourcePresentationRepository.class);
    private final ActivitySourceReferenceAuthorization locks = mock(ActivitySourceReferenceAuthorization.class);
    private final MaterializeLearningActivityUseCase materializer = mock(MaterializeLearningActivityUseCase.class);

    private GeneratedArtifactRepository originRepository(StudyMission mission) {
        GeneratedArtifactRepository repository = mock(GeneratedArtifactRepository.class);
        for (LearningActivity activity : mission.activities()) {
            if (activity.generatedArtifactId() != null) {
                when(repository.findObjectiveOrigin(activity.generatedArtifactId(), owner)).thenReturn(Optional.of(
                        new GeneratedArtifactRepository.ObjectiveOrigin(activity.generatedArtifactId(),
                                activity.learningObjectiveId(), mission.id(), owner)));
            }
        }
        return repository;
    }

    private StudyMission mission(int minutes, boolean presentation) {
        var activity = new LearningActivity(activityId, objectiveId,
                presentation ? LearningActivityType.UNDERSTAND : LearningActivityType.RETRIEVE,
                presentation ? LearningActionType.UNDERSTAND : LearningActionType.RETRIEVE,
                null, null, presentation ? "PENDING" : "COMPLETED", LearningDifficulty.FOUNDATIONAL, 1,
                UUID.randomUUID(), true, now.minusSeconds(30), presentation ? null : now.minusSeconds(10),
                now.minusSeconds(30), Set.of(source));
        return new StudyMission(missionId, owner, UUID.randomUUID(), null, StudyMissionStatus.ACTIVE,
                LearningStage.RETRIEVAL, StudyMissionGroundingMode.STRICT_SOURCE, minutes,
                now.minusSeconds(30), null, null, activityId,
                List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)),
                List.of(new LearningObjective(objectiveId, "Recall", "recall", "Recall", 1,
                        LearningObjectiveStatus.ACTIVE, now.minusSeconds(30))),
                List.of(activity), now.minusSeconds(30), now.minusSeconds(10));
    }

    private StudyMissionContinuationDecision configure(StudyMission mission, String outcome, String correctOption) {
        when(missions.findOwnedById(missionId, owner)).thenReturn(Optional.of(mission));
        when(missions.findOwnedByIdForUpdate(missionId, owner)).thenReturn(Optional.of(mission));
        when(missions.findFreshOwnedByIdForUpdate(missionId, owner)).thenReturn(Optional.of(mission));
        when(missions.save(any())).thenAnswer(call -> call.getArgument(0));
        var attempt = new StudentAttempt(UUID.randomUUID(), owner, activityId, 1,
                "The muscles that lift the wrist are on the dorsal side.", null, now.minusSeconds(10), outcome,
                null, null, now.minusSeconds(10), UUID.randomUUID(),
                "UNGRADED".equals(outcome) ? "EVALUATION_UNRESOLVED" : null, null);
        when(attempts.findOwnedByActivity(activityId, owner)).thenReturn(List.of(attempt));
        when(contracts.findValidatedForActivity(any(), any(), eq(owner))).thenReturn(Optional.of(
                new ActivityResponseContractRepository.ResponseContract("Question", List.of("Recall"),
                        "Expected", correctOption, "Feedback")));
        when(contracts.classify(any(), eq(owner), eq("STRICT_SOURCE")))
                .thenReturn(ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING);
        when(sources.resolveAuthorized(eq(owner), anyList(), anySet())).thenReturn(Optional.of(List.of()));
        when(locks.lockAndValidateForCompletion(eq(owner), anyList(), anySet(), anyBoolean())).thenReturn(true);
        var durations = new EnumMap<LearningActionType, Integer>(LearningActionType.class);
        for (var action : LearningActionType.values()) durations.put(action, 2);
        var engine = new LearningEngine(new LearningPolicyConfiguration(2, 3, 4, 5, 2,
                LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW, durations));
        return new StudyMissionContinuationDecision(new AssessmentAvailabilityResolver(contracts, attempts, sources,
                originRepository(mission)),
                engine, Sec004TestSupport.assembler(contracts), sources);
    }

    private ContinueStudyMissionUseCase continuation(StudyMissionContinuationDecision decision) {
        return new ContinueStudyMissionUseCase(user, missions, contracts, decision, materializer,
                new PersistPresentationCompletion(missions, contracts, locks, mock(GeneratedArtifactRepository.class)),
                new ChangeStudyMissionStatusUseCase(user, missions, new MissionStateMachinePolicy(), clock, decision, locks), clock);
    }

    @Test
    void savedUnassessableResponseReturnsRepeatableLimitationWithoutWritesOrMaterialization() {
        var mission = mission(20, false);
        var useCase = continuation(configure(mission, "UNGRADED", null));
        var command = new ContinueStudyMissionUseCase.Command(missionId, activityId, true);
        assertThat(useCase.execute(command)).isEqualTo(useCase.execute(command))
                .satisfies(result -> {
                    assertThat(result.outcome()).isEqualTo("NO_ACTIVITY");
                    assertThat(result.materializedActivityId()).isNull();
                    assertThat(result.actionType()).isEqualTo("COMMUNICATE_LIMITATION");
                    assertThat(result.rationaleCode()).isEqualTo("ASSESSMENT_UNAVAILABLE");
                });
        verifyNoInteractions(materializer);
        verify(missions, never()).save(any());
        verify(attempts, never()).append(any());
    }

    @Test
    void legacyUnavailableDenialCannotPrepareOrTransitionFollowUpIntents() {
        var mission = mission(20, false);
        var decision = configure(mission, "UNGRADED", null);
        var followUps = mock(FollowUpIntentLifecycle.class);
        var useCase = new ContinueStudyMissionUseCase(user, missions, contracts, decision, materializer,
                new PersistPresentationCompletion(missions, contracts, locks, mock(GeneratedArtifactRepository.class)),
                new ChangeStudyMissionStatusUseCase(user, missions, new MissionStateMachinePolicy(), clock,
                        decision, locks, followUps), clock, followUps);
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> useCase.execute(
                new ContinueStudyMissionUseCase.Command(missionId, activityId)))
                .hasMessageContaining("Reload the application");
        verify(followUps, never()).prepare(any(), any(), any());
        verify(followUps, never()).control(any(), any(), any(), any());
        verify(missions, never()).save(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void completionCancelsFollowUpObligationsOnlyAfterFinalSourceAuthorization() {
        var mission = mission(0, false);
        var decision = configure(mission, "UNGRADED", null);
        var followUps = mock(FollowUpIntentLifecycle.class);
        when(followUps.completionSources(any())).thenReturn(new FollowUpIntentLifecycle.CompletionSources(List.of(),Set.of()));
        var completion = new ChangeStudyMissionStatusUseCase(user, missions, new MissionStateMachinePolicy(),
                clock, decision, locks, followUps);
        var snapshot = new ChangeStudyMissionStatusUseCase.CompletionSnapshot(mission.updatedAt(), activityId,
                mission.activities().getFirst().completedAt(), decision.decide(mission, mission.activities().getFirst(), now).history());
        when(locks.lockAndValidateForCompletion(any(), anyList(), anySet(), anyBoolean())).thenReturn(false);
        assertThatThrownBy(() -> completion.completeFromContinuation(missionId, snapshot))
                .isInstanceOf(ActivityMaterializationException.class);
        verify(followUps, never()).cancelAll(any(), any(), any());
        verify(missions, never()).save(any());
        when(locks.lockAndValidateForCompletion(any(), anyList(), anySet(), anyBoolean())).thenReturn(true);
        assertThat(completion.completeFromContinuation(missionId, snapshot).status()).isEqualTo("COMPLETED");
        var order = inOrder(followUps, missions);
        order.verify(followUps).cancelAll(mission, FollowUpIntent.Reason.TERMINAL_COMPLETION, now);
        order.verify(missions).save(any());
        verifyNoInteractions(materializer);
        verify(attempts, never()).append(any());
    }

    @Test
    void completionLocksConsultedFollowUpDependenciesBeforeFinalDecisionAndCancellation() {
        var mission=mission(0,false);
        var decision=configure(mission,"UNGRADED",null);
        var followUps=mock(FollowUpIntentLifecycle.class);
        var consulted=new FollowUpIntentLifecycle.CompletionSources(mission.materials(),Set.of(source));
        when(followUps.completionSources(mission)).thenReturn(consulted);
        when(locks.lockAndValidateForCompletion(owner,mission.materials(),Set.of(source),true,
                consulted.materials(),consulted.references())).thenReturn(true);
        var completion=new ChangeStudyMissionStatusUseCase(user,missions,new MissionStateMachinePolicy(),
                clock,decision,locks,followUps);
        var snapshot=new ChangeStudyMissionStatusUseCase.CompletionSnapshot(mission.updatedAt(),activityId,
                mission.activities().getFirst().completedAt(),decision.decide(mission,mission.activities().getFirst(),now).history());
        assertThat(completion.completeFromContinuation(missionId,snapshot).status()).isEqualTo("COMPLETED");
        var order=inOrder(followUps,locks,missions);
        order.verify(followUps).completionSources(mission);
        order.verify(locks).lockAndValidateForCompletion(owner,mission.materials(),Set.of(source),true,
                consulted.materials(),consulted.references());
        order.verify(followUps).cancelAll(mission,FollowUpIntent.Reason.TERMINAL_COMPLETION,now);
        order.verify(missions).save(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void ordinaryUnknownUncertaintyDoesNotAcquirePermanentIneligibilityFacts() {
        var mission = mission(20, false);
        configure(mission, "UNGRADED", null);
        var resolver = new AssessmentAvailabilityResolver(contracts, attempts, sources, originRepository(mission));
        when(contracts.findValidatedForActivity(any(), any(), any())).thenReturn(Optional.empty());
        when(contracts.classify(any(), any(), any()))
                .thenReturn(ActivityResponseContractRepository.ActivityContractKind.UNKNOWN);
        var facts = resolver.resolve(mission, mission.activities().getFirst(), resolver.history(mission));
        assertThat(facts.activities().get(activityId)).isEqualTo(AssessmentAvailability.Kind.UNKNOWN);
        var retry = new NextLearningAction(LearningActionType.RETRY, objectiveId, "recall",
                LearningDifficulty.FOUNDATIONAL, "UNGRADED_RETRY", true);
        assertThat(AssessmentAvailability.unspecified().constrain(retry)).isSameAs(retry);
        assertThat(facts.constrain(retry)).isSameAs(retry);
    }

    @Test
    void legacyLimitationAndPendingPresentationAreDeniedBeforeWrites() {
        var mission = mission(20, false);
        var useCase = continuation(configure(mission, "UNGRADED", null));
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId)))
                .isInstanceOf(ActivityMaterializationException.class)
                .hasMessageContaining("Reload the application");
        var pending = mission(20, true);
        when(missions.findOwnedById(missionId, owner)).thenReturn(Optional.of(pending));
        when(contracts.findValidatedForActivity(any(), any(), any())).thenReturn(Optional.empty());
        when(contracts.classify(any(), eq(owner), anyString()))
                .thenReturn(ActivityResponseContractRepository.ActivityContractKind.PRESENTATION);
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId)))
                .hasMessageContaining("Reload the application");
        verify(missions, never()).save(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void timeSelectedCompletionRevalidatesAndChangesOnlyMissionLifecycle() {
        var mission = mission(0, false);
        var result = continuation(configure(mission, "UNGRADED", null))
                .execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true));
        assertThat(result.actionType()).isEqualTo("COMPLETE");
        var captor = org.mockito.ArgumentCaptor.forClass(StudyMission.class);
        verify(missions).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(StudyMissionStatus.COMPLETED);
        assertThat(saved.completedAt()).isEqualTo(now);
        assertThat(saved.activities()).isEqualTo(mission.activities());
        assertThat(saved.materials()).isEqualTo(mission.materials());
        verify(locks).lockAndValidateForCompletion(owner, mission.materials(), Set.of(source), true);
        verify(attempts, never()).append(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void failedSourceAuthorizationCannotCompleteOrBecomeLimitation() {
        var mission = mission(0, false);
        var useCase = continuation(configure(mission, "UNGRADED", null));
        when(locks.lockAndValidateForCompletion(any(), anyList(), anySet(), anyBoolean())).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isInstanceOf(ActivityMaterializationException.class).hasMessageContaining("sources");
        verify(missions, never()).save(any());
    }

    @Test
    void reusedIdentityAndGeneratedRelationshipsCannotBypassAvailability() {
        var facts = new AssessmentAvailability(true, activityId, Map.of(activityId, AssessmentAvailability.Kind.UNAVAILABLE));
        for (var type : List.of(LearningActionType.RETRY, LearningActionType.REDUCE_DIFFICULTY,
                LearningActionType.CONNECT, LearningActionType.APPLY)) {
            // Fact checking does not give a model-generated label or expected answer medical authority.
            var action = new NextLearningAction(type, objectiveId, "recall", LearningDifficulty.FOUNDATIONAL,
                    "candidate", false);
            assertThat(facts.constrain(action).actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
        }
        var reused = new NextLearningAction(LearningActionType.REUSE_VALIDATED_CONTENT, objectiveId, "recall",
                LearningDifficulty.FOUNDATIONAL, "reuse", false, LearningActionConstraints.unconstrained(), activityId);
        assertThat(facts.constrain(reused).actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
    }

    @Test
    void directMaterializationCannotRegenerateKnownUnassessableWorkOrCallProviders() {
        var mission = mission(20, false);
        configure(mission, "UNGRADED", null);
        var evidence = mock(ActivityEvidencePort.class);
        var ai = mock(ActivityAiTaskPort.class);
        var persistence = mock(PersistMaterializedActivity.class);
        var direct = new MaterializeLearningActivityUseCase(user, missions, evidence, ai, persistence, clock,
                new AssessmentAvailabilityResolver(contracts, attempts, sources, originRepository(mission)));
        for (var type : List.of(LearningActionType.RETRY, LearningActionType.CONNECT, LearningActionType.APPLY)) {
            var action = new NextLearningAction(type, objectiveId, "recall", LearningDifficulty.FOUNDATIONAL,
                    "candidate", type != LearningActionType.APPLY);
            assertThatThrownBy(() -> direct.execute(new MaterializeLearningActivityUseCase.Command(
                    missionId, action, activityId))).isInstanceOf(ActivityMaterializationException.class);
        }
        verifyNoInteractions(evidence, ai, persistence);
        verify(missions, never()).save(any());
        verify(attempts, never()).append(any());
    }

    @Test
    void deterministicUncertaintyAndHigherPriorityNonResponseActionsRemainAvailable() {
        var facts = new AssessmentAvailability(true, activityId,
                Map.of(activityId, AssessmentAvailability.Kind.DETERMINISTIC));
        for (var type : List.of(LearningActionType.RETRY, LearningActionType.COMPLETE,
                LearningActionType.PAUSE, LearningActionType.SOURCE_ONLY,
                LearningActionType.COMMUNICATE_LIMITATION, LearningActionType.REFLECT)) {
            var action = new NextLearningAction(type, objectiveId, "recall", null, "existing-policy", false);
            assertThat(facts.constrain(action)).isSameAs(action);
        }
    }

    @Test
    void timeOrPolicySelectedFreeTextIsDeniedWithoutIntroducingMcqSubstitution() {
        var facts = new AssessmentAvailability(true, activityId,
                Map.of(activityId, AssessmentAvailability.Kind.UNAVAILABLE));
        for (var subtype : RetrievalActivityType.values()) {
            var constraints = LearningActionConstraints.unconstrained().withRetrievalActivityType(subtype);
            var selected = new NextLearningAction(LearningActionType.RETRIEVE, objectiveId, "recall",
                    LearningDifficulty.FOUNDATIONAL, "TIME_LIMIT", true, constraints);
            var constrained = facts.constrain(selected);
            assertThat(constrained.actionType()).isEqualTo(subtype == RetrievalActivityType.MCQ
                    ? LearningActionType.RETRIEVE : LearningActionType.COMMUNICATE_LIMITATION);
            if (subtype == RetrievalActivityType.MCQ) assertThat(constrained).isSameAs(selected);
        }
    }

    @Test
    void presentationContentionIsTranslatedAfterTheWriteBoundaryWithoutDispatchingAnotherAction() {
        var pending = mission(20, true);
        var decision = configure(pending, "UNGRADED", null);
        when(contracts.classify(any(), eq(owner), anyString()))
                .thenReturn(ActivityResponseContractRepository.ActivityContractKind.PRESENTATION);
        var persistence = mock(PersistPresentationCompletion.class);
        when(persistence.persist(any())).thenThrow(new SourceDependencyBusyException(null));
        var useCase = new ContinueStudyMissionUseCase(user, missions, contracts, decision, materializer,
                persistence, mock(ChangeStudyMissionStatusUseCase.class), clock);
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION);
        verify(missions, never()).save(any());
        verify(attempts, never()).append(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void lifecyclePreparationContentionReturnsStaleBeforeDispatch() {
        var mission = mission(20, false);
        var decision = configure(mission, "UNGRADED", null);
        var followUps = mock(FollowUpIntentLifecycle.class);
        when(followUps.prepare(missionId, owner, now)).thenThrow(new SourceDependencyBusyException(null));
        var useCase = new ContinueStudyMissionUseCase(user, missions, contracts, decision, materializer,
                mock(PersistPresentationCompletion.class), mock(ChangeStudyMissionStatusUseCase.class), clock, followUps);
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isInstanceOfSatisfying(ActivityMaterializationException.class,
                        error -> assertThat(error.reason()).isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION));
        verify(followUps, never()).control(any(), any(), any(), any());
        verify(missions, never()).save(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void lifecycleControlContentionCannotReturnSuccessfulNoActivity() {
        var mission = mission(20, false);
        var decision = configure(mission, "UNGRADED", null);
        var followUps = mock(FollowUpIntentLifecycle.class);
        when(followUps.control(any(), isNull(), any(), eq(now))).thenThrow(new SourceDependencyBusyException(null));
        var useCase = new ContinueStudyMissionUseCase(user, missions, contracts, decision, materializer,
                mock(PersistPresentationCompletion.class), mock(ChangeStudyMissionStatusUseCase.class), clock, followUps);
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isInstanceOfSatisfying(ActivityMaterializationException.class,
                        error -> assertThat(error.reason()).isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION));
        verify(missions, never()).save(any());
        verifyNoInteractions(materializer);
    }

    @Test
    void lifecycleStatusContentionCannotSaveAChangedMission() {
        var mission = mission(20, false);
        var decision = configure(mission, "UNGRADED", null);
        var followUps = mock(FollowUpIntentLifecycle.class);
        doThrow(new SourceDependencyBusyException(null)).when(followUps).lockForDecision(any());
        var useCase = new ChangeStudyMissionStatusUseCase(user, missions, new MissionStateMachinePolicy(),
                clock, decision, locks, followUps);
        assertThatThrownBy(() -> useCase.execute(missionId, ChangeStudyMissionStatusUseCase.Command.PAUSE))
                .isInstanceOfSatisfying(ActivityMaterializationException.class,
                        error -> assertThat(error.reason()).isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION));
        verify(followUps, never()).revalidate(any(), any());
        verify(missions, never()).save(any());
        verify(attempts, never()).append(any());
    }

    @Test
    void completionConflictsAndInfrastructureFailuresDoNotBecomeSuccessfulNoActivity() {
        var mission = mission(0, false);
        var useCase = continuation(configure(mission, "UNGRADED", null));
        doThrow(new SourceDependencyBusyException(null)).when(locks)
                .lockAndValidateForCompletion(any(), anyList(), anySet(), anyBoolean());
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isInstanceOf(ActivityMaterializationException.class)
                .hasMessage("The mission changed. Reload its current state.")
                .satisfies(error -> assertThat(((ActivityMaterializationException) error).reason())
                        .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION));
        var infrastructure = new IllegalStateException("Synthetic infrastructure failure");
        // when(mock.method()) would execute the existing throwing stub while replacing it.
        doThrow(infrastructure).when(locks)
                .lockAndValidateForCompletion(any(), anyList(), anySet(), anyBoolean());
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId, true)))
                .isSameAs(infrastructure);
        verify(locks, times(2)).lockAndValidateForCompletion(owner, mission.materials(), Set.of(source), true);
        verify(missions, never()).save(any());
        verify(attempts, never()).append(any());
        verifyNoInteractions(materializer);
    }
}
