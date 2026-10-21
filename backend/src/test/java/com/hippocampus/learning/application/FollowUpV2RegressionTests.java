package com.hippocampus.learning.application;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.domain.error.DomainConflictException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real engine, assembler, Continue, materialization and persistence use cases; only ports are fakes. */
class FollowUpV2RegressionTests {
    @Test void correctMcqRequiresARealUnderstandingCheckAndUngradedDoesNotUnlockIt() {
        var f=new Fixture();
        StudentAttempt original=f.attempts.getFirst();
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.RECALL,EvidenceStrength.DEVELOPING)).isTrue();
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isFalse();
        UUID origin=f.mission.currentActivityId();
        f.next(); // One valid prerequisite presentation, not another graded-target retrieval.
        assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(f.intents.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.PREREQUISITE_SUPPORT);
        f.next(); // Complete presentation through real persistence, then select the required check.
        assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
        assertThat(f.attempts).containsExactly(original);
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isFalse();
        assertThatThrownBy(() -> f.continuation().execute(new ContinueStudyMissionUseCase.Command(f.mission.id(),origin)))
                .isInstanceOf(ActivityMaterializationException.class);
        f.submit("UNGRADED");
        var unchanged=f.intents.rows.getFirst();
        int activityCount=f.mission.activities().size(),calls=f.generations.get();
        f.assertConflict();
        f.assertConflict(); // A reconstructed use case simulates refresh, not cached state.
        f.status(MissionLifecycleState.PAUSED);
        f.status(MissionLifecycleState.ACTIVE);
        f.assertConflict();
        assertThat(f.mission.activities()).hasSize(activityCount);
        assertThat(f.generations.get()).isEqualTo(calls);
        assertThat(f.intents.rows).containsExactly(unchanged);
        assertThat(f.intents.rows.getFirst().terminal()).isFalse();
        assertThat(f.attempts).hasSize(2).contains(original);
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isFalse();
        assertThat(f.mission.status()).isEqualTo(StudyMissionStatus.ACTIVE);
    }

    @Test void syntheticSafelyGradedPrerequisiteRestoresEligibilityWithoutBypassingAntiRepetition() {
        var f=new Fixture();
        UUID originalIntent=f.intents.rows.getFirst().id();
        f.next(); f.next();
        // Synthetic qualified outcome exercises the contract; no new production grading path exists.
        f.submit("CORRECT");
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isTrue();
        assertThat(f.intents.rows).hasSize(2);
        assertThat(f.intents.rows.stream().filter(i -> i.id().equals(originalIntent)).findFirst().orElseThrow().terminal()).isFalse();
        f.lifecycle().prepare(f.mission.id(),f.owner,f.now);
        assertThat(f.intents.rows).noneMatch(FollowUpIntent::terminal);
        var policy=new GradedResponseFollowUpPolicy();
        var normal=new NextLearningAction(LearningActionType.RETRIEVE,f.objective.id(),"target",
                LearningDifficulty.FOUNDATIONAL,"TEST",true,
                LearningActionConstraints.unconstrained().withRetrievalActivityType(RetrievalActivityType.SHORT_ANSWER));
        assertThat(policy.select(f.state(),normal,f.intents.rows.getFirst()).rationaleCode()).isEqualTo("GRADED_RESPONSE_FOLLOW_UP");
        f.assertConflict(); // Eligibility permits reconsideration; the same target still collides.
        assertThat(f.attempts).hasSize(2);
    }

    @Test void genuineReplacementAttemptSupersedesWhilePrerequisiteObligationsRemainDistinct() {
        var f=new Fixture();
        f.next(); f.next();
        f.submit("INCORRECT"); // Explicitly synthetic prerequisite grading.
        var root=f.intents.rows.getFirst();
        var prerequisite=f.intents.rows.getLast();
        assertThat(root.terminal()).isFalse();
        assertThat(prerequisite.grade()).isEqualTo(AttemptOutcome.INCORRECT);
        var prepared=f.lifecycle().prepare(f.mission.id(),f.owner,f.now);
        assertThat(prepared.id()).isEqualTo(prerequisite.id());
        assertThat(f.intents.rows).noneMatch(FollowUpIntent::terminal);
        // A later response to the original assessment is a replacement, not prerequisite evidence.
        var replacement=new StudentAttempt(UUID.randomUUID(),f.owner,root.originatingActivityId(),2,
                "new answer",null,f.now,"CORRECT",null,"CORRECT",f.now);
        f.attempts.add(replacement);
        f.lifecycle().revalidate(f.mission,f.now);
        assertThat(f.intents.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SUPERSEDED);
        assertThat(f.intents.rows.getLast().id()).isEqualTo(prerequisite.id());
        assertThat(f.intents.rows.getLast().terminal()).isFalse();
    }

    @Test void reflectionStageCannotCompleteOrRegenerateAnExhaustedFollowUp() {
        var f=new Fixture();
        f.next();f.next();f.submit("UNGRADED");
        var reflection=new LearningActivity(UUID.randomUUID(),f.objective.id(),LearningActivityType.REFLECT,
                LearningActionType.REFLECT,null,null,"PENDING",null,5,null,false,null,null,f.now,Set.of());
        var activities=new ArrayList<>(f.mission.activities());activities.add(reflection);
        f.mission=f.copy(f.mission.status(),LearningStage.REFLECTION,reflection.id(),activities);
        int calls=f.generations.get();
        f.assertConflict(); // May complete the existing presentation; cannot create another fallback.
        f.assertConflict();
        assertThat(f.mission.learningState()).isEqualTo(LearningStage.EVIDENCE_UPDATE);
        assertThat(f.mission.status()).isEqualTo(StudyMissionStatus.ACTIVE);
        assertThat(f.generations.get()).isEqualTo(calls);
        assertThat(f.mission.activities()).hasSize(5);
    }

    @Test void ownerAndSourceLossAreRevalidatedAndTerminalIntentCannotResurrect() {
        var f=new Fixture();
        assertThatThrownBy(() -> f.lifecycle().prepare(f.mission.id(),UUID.randomUUID(),f.now))
                .isInstanceOf(IllegalStateException.class);
        when(f.sources.allFrozenSourcesAvailable(eq(f.owner),anyList())).thenReturn(false);
        f.lifecycle().prepare(f.mission.id(),f.owner,f.now);
        assertThat(f.intents.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SOURCE_LOSS);
        when(f.sources.allFrozenSourcesAvailable(eq(f.owner),anyList())).thenReturn(true);
        f.status(MissionLifecycleState.PAUSED);f.status(MissionLifecycleState.ACTIVE);
        assertThat(f.intents.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.CANCELED);
        assertThat(f.generations.get()).isZero();
    }

    @Test void validCorrectiveIntentMaterializesExactlyOnceAndDuplicateContinueReturnsItsLink() {
        var f=new Fixture(AttemptOutcome.INCORRECT);
        f.establishUnderstandingBeforeOrigin(); // Genuine assessed fixture, not presentation evidence.
        UUID origin=f.current().id();
        f.next();
        UUID linked=f.current().id();
        assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.RETRIEVE);
        assertThat(f.intents.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.MATERIALIZED);
        assertThat(f.intents.rows.getFirst().linkedActivityId()).isEqualTo(linked);
        assertThat(f.continuation().execute(new ContinueStudyMissionUseCase.Command(f.mission.id(),origin)).materializedActivityId()).isEqualTo(linked);
        assertThat(f.generations.get()).isEqualTo(1);
        assertThat(f.mission.activities()).hasSize(3);
        assertThat(f.attempts).hasSize(2);
    }

    @Test void incorrectAndSyntheticPartialOriginsCannotReplacePrerequisitesWithCorrectiveRetrieval() {
        for(var grade:List.of(AttemptOutcome.INCORRECT)) {
            var f=new Fixture(grade); // PARTIAL is synthetic, not a production MCQ outcome.
            f.next();
            assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
            f.next();
            assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
            assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isFalse();
            assertThat(f.intents.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.DEFERRED);
        }
    }

    @Test void prerequisiteOriginObligationsSurviveFurtherUngradedAndGradedPrerequisiteResponses() {
        for(String outcome:List.of("UNGRADED","CORRECT")) {
            var f=new Fixture();f.next();f.next();f.submit("INCORRECT");
            UUID checkIntent=f.intents.rows.getLast().id();
            f.next();
            assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
            f.next();
            assertThat(f.current().representedActionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
            f.submit(outcome);
            f.lifecycle().prepare(f.mission.id(),f.owner,f.now);
            assertThat(f.intents.rows).noneMatch(FollowUpIntent::terminal);
            assertThat(f.intents.rows).anyMatch(i -> i.id().equals(checkIntent));
            if(outcome.equals("UNGRADED")) {
                int calls=f.generations.get();f.assertConflict();f.assertConflict();
                assertThat(f.generations.get()).isEqualTo(calls);
                assertThat(f.intents.rows).hasSize(2);
            } else {
                assertThat(f.intents.rows).hasSize(3);
                assertThat(f.intents.rows).noneMatch(i -> i.state()==FollowUpIntent.State.DEFERRED);
                // A blocked newer obligation must not starve an eligible older corrective obligation.
                assertThat(f.lifecycle().prepare(f.mission.id(),f.owner,f.now).id()).isEqualTo(checkIntent);
                UUID completedCheck=f.current().id();
                f.next();
                assertThat(f.intents.rows.stream().filter(i -> i.state()==FollowUpIntent.State.MATERIALIZED).count()).isEqualTo(1);
                UUID linked=f.current().id();int calls=f.generations.get();
                assertThat(f.continuation().execute(new ContinueStudyMissionUseCase.Command(f.mission.id(),completedCheck)).materializedActivityId()).isEqualTo(linked);
                assertThat(f.lifecycle().prepare(f.mission.id(),f.owner,f.now).state()).isEqualTo(FollowUpIntent.State.MATERIALIZED);
                f.submit("UNGRADED");
                assertThat(f.intents.rows.stream().filter(i -> i.id().equals(checkIntent)).findFirst().orElseThrow().state()).isEqualTo(FollowUpIntent.State.SATISFIED);
                assertThat(f.intents.rows).hasSize(3);
                assertThat(f.generations.get()).isEqualTo(calls);
            }
        }
    }

    @Test void terminalControlCancelsAllCoexistingObligationsAtomically() {
        var f=new Fixture();f.next();f.next();f.submit("INCORRECT");
        assertThat(f.intents.rows).hasSize(2);
        var m=f.mission;
        f.mission=new StudyMission(m.id(),m.userId(),m.topicId(),m.subtopicId(),m.status(),m.learningState(),m.groundingMode(),
                0,m.startedAt(),m.completedAt(),m.stoppedAt(),m.currentActivityId(),m.materials(),m.objectives(),m.activities(),m.createdAt(),m.updatedAt());
        var completion=f.engine.decide(f.state(),f.intents.rows.getFirst());
        assertThat(f.lifecycle().control(f.mission,f.intents.rows.getFirst(),completion,f.now)).isTrue();
        assertThat(f.intents.rows).allMatch(i -> i.state()==FollowUpIntent.State.CANCELED
                && i.reason()==FollowUpIntent.Reason.TERMINAL_COMPLETION);
        assertThat(f.mission.status()).isEqualTo(StudyMissionStatus.COMPLETED);
    }

    @Test void timePolicyRetainsPrecedenceOverNoProgressConflict() {
        var f=new Fixture();f.next();f.next();f.submit("UNGRADED");
        var s=f.state();
        var expired=new LearningState(s.missionId(),s.learningObjectiveId(),s.conceptKey(),s.missionState(),
                s.currentStage(),s.evidence(),s.sourceCapability(),new LearningTimeContext(30,0,30),
                s.recentActivityHistory(),s.connectionRelevant(),s.actionConstraints());
        assertThat(f.engine.decide(expired,f.intents.rows.getFirst()).actionType()).isEqualTo(LearningActionType.COMPLETE);
    }

    @Test void sharedReadDecisionPreservesIntentAndMatchesMaterializedFollowUp() {
        var f = new Fixture(AttemptOutcome.INCORRECT);
        f.establishUnderstandingBeforeOrigin();
        var before = List.copyOf(f.intents.rows);
        StudyMissionSourcePresentationRepository sourceViews = (owner, scopes, ids) -> Optional.of(List.of());
        var contracts = mock(ActivityResponseContractRepository.class);
        var decision = new StudyMissionContinuationDecision(new AssessmentAvailabilityResolver(contracts,
                f.attemptRepository, sourceViews, f.artifacts, f.assembler.eligibility()), f.engine, f.assembler, sourceViews, f.lifecycle());
        var read = decision.decide(f.mission, f.current(), f.now).action();
        assertThat(read.rationaleCode()).isEqualTo("GRADED_RESPONSE_FOLLOW_UP");
        assertThat(f.intents.rows).containsExactlyElementsOf(before);
        verify(f.missions, never()).save(any());
        assertThat(f.generations.get()).isZero();
        f.next();
        assertThat(f.current().questionIntent()).isEqualTo(read.constraints().questionIntent());
        assertThat(f.intents.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.MATERIALIZED);
    }

    @Test void blockedMissionReadSurvivesRefreshPauseAndResumeWithoutUnlockingProgress() {
        var f = new Fixture(); f.next(); f.next(); f.submit("UNGRADED");
        var snapshot = f.mission;
        var obligations = List.copyOf(f.intents.rows);
        var history = List.copyOf(f.attempts);
        int calls = f.generations.get();
        for (int i = 0; i < 2; i++) {
            var result = f.read();
            assertThat(result.currentActivity().id()).isEqualTo(f.current().id());
            assertThat(result.continuation()).isNull();
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(f.mission).isEqualTo(snapshot);
            f.assertConflict();
        }
        f.status(MissionLifecycleState.PAUSED);
        assertThat(f.read().status()).isEqualTo("PAUSED");
        f.status(MissionLifecycleState.ACTIVE);
        assertThat(f.read().continuation()).isNull();
        f.assertConflict();
        assertThat(f.intents.rows).containsExactlyElementsOf(obligations);
        assertThat(f.attempts).containsExactlyElementsOf(history);
        assertThat(f.generations.get()).isEqualTo(calls);
        assertThat(f.state().evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)).isFalse();
    }

    @Test void sameTargetExhaustionRemainsReadableWithoutBypassingAntiRepetition() {
        var f = new Fixture(); f.next(); f.next(); f.submit("CORRECT"); // Synthetic contract fixture only.
        f.assertConflict(); // Write-side revalidation may release a completed prerequisite deferral.
        var obligations = List.copyOf(f.intents.rows);
        int calls = f.generations.get();
        assertThat(f.read().continuation()).isNull();
        assertThat(f.intents.rows).containsExactlyElementsOf(obligations);
        assertThat(f.generations.get()).isEqualTo(calls);
    }

    @Test void assessmentLimitationDoesNotCancelOrSatisfyAPendingGradedIntent() {
        var f = new Fixture();
        var before = List.copyOf(f.intents.rows);
        f.lifecycle().decision(before.getFirst(), new NextLearningAction(LearningActionType.COMMUNICATE_LIMITATION,
                f.objective.id(), "target", null, LearningRationaleCodes.ASSESSMENT_UNAVAILABLE, false), f.now);
        assertThat(f.intents.rows).containsExactlyElementsOf(before);
        assertThat(f.attempts).hasSize(1);
        assertThat(f.generations.get()).isZero();
    }

    @Test void historicalPartialOriginIsHiddenOnReadAndCanceledForSafetyOnWrite() {
        var f=new Fixture(AttemptOutcome.PARTIAL);
        var original=f.attempts.getFirst(); var intent=f.intents.rows.getFirst();
        assertThat(f.lifecycle().peek(f.mission,f.now)).isNull();
        assertThat(f.intents.rows).containsExactly(intent);
        assertThat(f.state().evidence().dimensions()).isEmpty();
        f.lifecycle().prepare(f.mission.id(),f.owner,f.now);
        assertThat(f.intents.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SAFETY);
        assertThat(f.attempts).containsExactly(original);
        assertThat(original.evaluationStatus()).isEqualTo("PARTIAL");
        assertThat(f.generations.get()).isZero();
    }

    static final class Fixture {
        final Instant now=Instant.parse("2026-10-18T00:00:00Z");
        final UUID owner=UUID.randomUUID();
        final LearningObjective objective=new LearningObjective(UUID.randomUUID(),"objective","target",null,1,LearningObjectiveStatus.ACTIVE,now);
        final List<StudentAttempt> attempts=new ArrayList<>();
        final FollowUpIntentLifecycleTests.MemoryIntents intents=new FollowUpIntentLifecycleTests.MemoryIntents();
        final StudyMissionRepository missions=mock(StudyMissionRepository.class);
        final StudentAttemptRepository attemptRepository=mock(StudentAttemptRepository.class);
        final ActivitySourceReferenceAuthorization sources=mock(ActivitySourceReferenceAuthorization.class);
        final GeneratedArtifactRepository artifacts=mock(GeneratedArtifactRepository.class);
        final AtomicInteger generations=new AtomicInteger();
        final Map<UUID,GeneratedArtifactRepository.GeneratedArtifact> savedArtifacts=new HashMap<>();
        final LearningEngine engine;
        final ActivityResponseContractRepository evidenceContracts=mock(ActivityResponseContractRepository.class);
        final Set<UUID> mcqActivities=new HashSet<>();
        final ActivityResponseContractRepository.ResponseContract mcq=new ActivityResponseContractRepository.ResponseContract(
                "Question",List.of("target"),"B","B","Feedback");
        final ActivityResponseContractRepository.ResponseContract written=new ActivityResponseContractRepository.ResponseContract(
                "Question",List.of("target"),"Answer",null,"Feedback");
        final StudyMissionLearningStateAssembler assembler=new StudyMissionLearningStateAssembler(
                new AttemptEvidenceEligibility(evidenceContracts,sources,artifacts));
        StudyMission mission;
        Fixture() { this(AttemptOutcome.CORRECT); }
        Fixture(AttemptOutcome grade) {
            var durations=new EnumMap<LearningActionType,Integer>(LearningActionType.class);
            for(var type:LearningActionType.values())durations.put(type,1);
            engine=new LearningEngine(new LearningPolicyConfiguration(2,3,4,5,2,
                    LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,durations));
            UUID missionId=UUID.randomUUID();
            var presentation=new LearningActivity(UUID.randomUUID(),objective.id(),LearningActivityType.UNDERSTAND,
                    LearningActionType.UNDERSTAND,null,null,"COMPLETED",LearningDifficulty.FOUNDATIONAL,1,null,false,now,now,now,Set.of());
            var origin=new LearningActivity(UUID.randomUUID(),objective.id(),LearningActivityType.RETRIEVE,
                    LearningActionType.RETRIEVE,FollowUpIntent.identity(objective.id(),"target"),null,"COMPLETED",
                    LearningDifficulty.FOUNDATIONAL,2,UUID.randomUUID(),false,now,now,now,Set.of());
            mission=new StudyMission(missionId,owner,UUID.randomUUID(),null,StudyMissionStatus.ACTIVE,LearningStage.RETRIEVAL,
                    StudyMissionGroundingMode.GENERAL_KNOWLEDGE,30,now,null,null,origin.id(),List.of(),List.of(objective),List.of(presentation,origin),now,now);
            if (grade != AttemptOutcome.PARTIAL) mcqActivities.add(origin.id());
            var original=new StudentAttempt(UUID.randomUUID(),owner,origin.id(),1,grade==AttemptOutcome.CORRECT?"B":"A",null,now,grade.name(),null,
                    grade==AttemptOutcome.PARTIAL?null:grade.name(),now);
            attempts.add(original);
            intents.insert(new GradedResponseFollowUpPolicy().create(UUID.randomUUID(),owner,missionId,original.id(),origin.id(),objective,
                    grade,new FollowUpIntent.Basis(grade==AttemptOutcome.PARTIAL?"VERIFIED_GAP":"DETERMINISTIC_MCQ",
                            grade==AttemptOutcome.PARTIAL?List.of("synthetic verified gap"):List.of()),
                    new FollowUpIntent.Scope(List.of(),Set.of(),mission.groundingMode()),now).orElseThrow());
            when(missions.findOwnedById(any(),any())).thenAnswer(i -> owner.equals(i.getArgument(1))&&mission.id().equals(i.getArgument(0))?Optional.of(mission):Optional.empty());
            when(missions.findOwnedByIdForUpdate(any(),any())).thenAnswer(i -> missions.findOwnedById(i.getArgument(0),i.getArgument(1)));
            when(missions.save(any())).thenAnswer(i -> {mission=i.getArgument(0);return mission;});
            when(attemptRepository.findOwnedByActivity(any(),any())).thenAnswer(i -> attempts.stream()
                    .filter(a -> a.learningActivityId().equals(i.getArgument(0))&&a.userId().equals(i.getArgument(1))).toList());
            when(attemptRepository.append(any())).thenAnswer(i -> {StudentAttempt a=i.getArgument(0);attempts.add(a);return a;});
            when(evidenceContracts.classify(any(),eq(owner),anyString())).thenReturn(ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING);
            when(evidenceContracts.findDeterministicForActivity(any(),eq(owner),anyString())).thenAnswer(i ->
                    mcqActivities.contains(((LearningActivity)i.getArgument(0)).id())
                            ? Optional.of(new ActivityResponseContractRepository.DeterministicContract(mcq,Set.of("A","B"))) : Optional.empty());
            when(evidenceContracts.findValidatedForActivity(any(),any(),eq(owner))).thenAnswer(i ->
                    Optional.of(mcqActivities.contains(i.getArgument(0))?mcq:written));
            when(sources.lockAndValidateForCompletion(eq(owner),anyList(),anySet(),anyBoolean(),anyList(),anySet())).thenReturn(true);
            when(sources.allFrozenSourcesAvailable(eq(owner),anyList())).thenReturn(true);
            when(sources.availableForRead(eq(owner),anyList(),anySet())).thenReturn(true);
            when(sources.allOwnedAndWithinMissionScope(eq(owner),anyList(),anySet())).thenReturn(true);
            when(artifacts.save(any())).thenAnswer(i -> { GeneratedArtifactRepository.GeneratedArtifact artifact=i.getArgument(0); savedArtifacts.put(artifact.id(),artifact); return artifact; });
            when(artifacts.findOwnedByIdForUpdate(any(),eq(owner))).thenAnswer(i -> Optional.ofNullable(savedArtifacts.get(i.getArgument(0)))
                    .or(() -> Optional.of(mock(GeneratedArtifactRepository.GeneratedArtifact.class))));
            when(artifacts.findSourceReferenceIdsForShare(any())).thenReturn(Set.of());
            when(missions.findFreshOwnedByIdForUpdate(any(),any())).thenAnswer(i -> missions.findOwnedById(i.getArgument(0),i.getArgument(1)));
        }
        GetStudyMissionUseCase.Result read() {
            when(artifacts.findOwnedById(any(),eq(owner))).thenAnswer(i -> Optional.ofNullable(savedArtifacts.get(i.getArgument(0))));
            when(artifacts.findSourceReferenceIds(any())).thenReturn(Set.of());
            StudyMissionSourcePresentationRepository views = (user, scope, ids) -> Optional.of(List.of());
            var contracts = mock(ActivityResponseContractRepository.class);
            var decision = new StudyMissionContinuationDecision(new AssessmentAvailabilityResolver(contracts,
                    attemptRepository, views, artifacts, assembler.eligibility()), engine, assembler, views, lifecycle());
            return new GetStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions, artifacts,
                    new com.hippocampus.bootstrap.JacksonGeneratedActivityContentDecoder(new tools.jackson.databind.ObjectMapper()),
                    views, attemptRepository, decision, Clock.fixed(now, ZoneOffset.UTC)).execute(mission.id(), true);
        }
        FollowUpIntentLifecycle lifecycle(){
            StudyMissionSourcePresentationRepository views=(u,scope,ids)->Optional.of(List.of());
            var availability=new AssessmentAvailabilityResolver(evidenceContracts,attemptRepository,views,artifacts,assembler.eligibility());
            return new FollowUpIntentLifecycle(intents,missions,attemptRepository,sources,engine,assembler,availability);
        }
        LearningActivity current(){return mission.activities().stream().filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElseThrow();}
        LearningState state(){var history=new LinkedHashMap<UUID,List<StudentAttempt>>();for(var a:mission.activities())history.put(a.id(),attemptRepository.findOwnedByActivity(a.id(),owner));return assembler.assembleForFollowUp(mission,current(),history,now);}
        void establishUnderstandingBeforeOrigin(){
            var old=mission.activities().getFirst();
            var check=new LearningActivity(old.id(),objective.id(),LearningActivityType.RETRIEVE,
                    LearningActionType.UNDERSTANDING_CHECK,null,null,"COMPLETED",LearningDifficulty.FOUNDATIONAL,
                    1,UUID.randomUUID(),false,now,now,now,Set.of());
            var activities=new ArrayList<>(mission.activities());activities.set(0,check);
            mission=copy(mission.status(),mission.learningState(),mission.currentActivityId(),activities);
            mcqActivities.add(check.id());
            attempts.add(new StudentAttempt(UUID.randomUUID(),owner,check.id(),1,"B",null,now,"CORRECT",null,"CORRECT",now));
        }
        ContinueStudyMissionUseCase continuation(){
            var lifecycle=lifecycle();
            var contracts=mock(ActivityResponseContractRepository.class);
            when(contracts.classify(any(),eq(owner),eq("GENERAL_KNOWLEDGE"))).thenAnswer(i -> {
                LearningActivity activity=i.getArgument(0);
                if (Set.of(LearningActionType.UNDERSTAND,LearningActionType.HINT,LearningActionType.PREREQUISITE_SUPPORT).contains(activity.representedActionType())
                        && savedArtifacts.containsKey(activity.generatedArtifactId()))
                    return ActivityResponseContractRepository.ActivityContractKind.PRESENTATION;
                return ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING;
            });
            var sourceViews=mock(StudyMissionSourcePresentationRepository.class);
            when(sourceViews.resolveAuthorized(eq(owner),anyList(),anySet())).thenReturn(Optional.of(List.of()));
            when(sources.lockAndValidateForCompletion(eq(owner),anyList(),anySet(),anyBoolean())).thenReturn(true);
            var availability=new AssessmentAvailabilityResolver(contracts,attemptRepository,sourceViews,artifacts,assembler.eligibility());
            var decision=new StudyMissionContinuationDecision(availability,engine,assembler,sourceViews,lifecycle);
            var persistence=new PersistMaterializedActivity(missions,artifacts,sources,lifecycle,new com.hippocampus.bootstrap.FollowUpActivityContentValidator(),contracts,decision);
            ActivityAiTaskPort ai=request -> {generations.incrementAndGet();return new ActivityAiTaskPort.ValidatedContent(
                    "QUESTION","QUESTION_GENERATION","fixture","{\"activityType\":\"SHORT_ANSWER\",\"concept\":\"target\",\"learningObjective\":\"objective\",\"question\":\"Explain the target.\",\"options\":[],\"correctOption\":null,\"expectedAnswer\":\"Answer.\",\"explanation\":\"Feedback.\",\"difficulty\":\""+request.difficulty().name()+"\",\"sourceReferences\":[],\"limitations\":[]}","GENERAL_KNOWLEDGE","GENERAL_GENERATED",
                    "fixture","1","fixture","fixture",null,ActivityAiTaskPort.ValidationStatus.VALIDATED,false);};
            var materializer=new MaterializeLearningActivityUseCase(()->new AuthenticatedUser(owner),missions,
                    request->new ActivityEvidencePort.Evidence(Set.of()),ai,persistence,Clock.fixed(now,ZoneOffset.UTC),availability);
            return new ContinueStudyMissionUseCase(()->new AuthenticatedUser(owner),missions,contracts,decision,
                    materializer,new PersistPresentationCompletion(missions,contracts,sources,artifacts),
                    new ChangeStudyMissionStatusUseCase(()->new AuthenticatedUser(owner),missions,
                            new MissionStateMachinePolicy(),Clock.fixed(now,ZoneOffset.UTC),decision,sources,lifecycle),
                    Clock.fixed(now,ZoneOffset.UTC),lifecycle);
        }
        void next(){continuation().execute(new ContinueStudyMissionUseCase.Command(mission.id(),current().id(),true));}
        void assertConflict(){assertThatThrownBy(this::next).isInstanceOfSatisfying(DomainConflictException.class,
                e -> assertThat(e.errorCode().value()).isEqualTo("FOLLOW_UP_NO_PROGRESS"));}
        void submit(String grade){
            if (!grade.equals("UNGRADED")) mcqActivities.add(current().id());
            var useCase=new SubmitActivityResponseUseCase(() -> new AuthenticatedUser(owner),missions,attemptRepository,
                    evidenceContracts, request -> new ResponseEvaluationPort.Result(ResponseEvaluationPort.Outcome.UNCERTAIN,
                            List.of(),List.of(),List.of(),"limited",ResponseEvaluationPort.Certainty.LIMITED,
                            ResponseEvaluationPort.RecommendedAction.RETRY,true),
                    (u,m,a,mode,scope,refs) -> new AuthorizedResponseEvidence(mode,List.of()),engine,assembler,
                    new PersistActivityResponse(missions,attemptRepository,lifecycle(),assembler,engine),Clock.fixed(now,ZoneOffset.UTC));
            useCase.execute(new SubmitActivityResponseUseCase.Command(mission.id(),current().id(),
                    grade.equals("UNGRADED")?"real answer":null,null,grade.equals("UNGRADED")?null:grade.equals("CORRECT")?"B":"A",UUID.randomUUID()));
        }
        void status(MissionLifecycleState state){new ChangeStudyMissionStatusUseCase(()->new AuthenticatedUser(owner),missions,
                new MissionStateMachinePolicy(),Clock.fixed(now,ZoneOffset.UTC),lifecycle()).execute(mission.id(),state==MissionLifecycleState.PAUSED?ChangeStudyMissionStatusUseCase.Command.PAUSE:ChangeStudyMissionStatusUseCase.Command.RESUME);}
        StudyMission copy(StudyMissionStatus status,LearningStage stage,UUID current,List<LearningActivity> activities){return new StudyMission(mission.id(),owner,mission.topicId(),null,status,stage,
                mission.groundingMode(),30,now,null,null,current,mission.materials(),mission.objectives(),activities,now,now);}
    }
}
