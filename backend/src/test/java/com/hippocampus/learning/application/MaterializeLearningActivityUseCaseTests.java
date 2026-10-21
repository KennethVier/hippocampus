package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.ApplicationActivityLevel;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RetrievalActivityType;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository;
import tools.jackson.databind.ObjectMapper;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class MaterializeLearningActivityUseCaseTests {

    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID TOPIC_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();

    private InMemoryMissionRepository missions;
    private RecordingEvidencePort evidence;
    private RecordingAiPort ai;
    private InMemoryArtifactRepository artifacts;
    private Set<UUID> deniedSources;
    private MaterializeLearningActivityUseCase useCase;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void initialMaterializationWithoutClaimAwareExposureWiringFailsBeforeRetrieval(boolean expired) {
        var mission=InitialStudyMissionStateAssemblerTests.mission(20);
        var repository=org.mockito.Mockito.mock(StudyMissionRepository.class);
        var evidence=org.mockito.Mockito.mock(ActivityEvidencePort.class);
        var ai=org.mockito.Mockito.mock(ActivityAiTaskPort.class);
        var persistence=org.mockito.Mockito.mock(PersistMaterializedActivity.class);
        org.mockito.Mockito.when(repository.findOwnedById(mission.id(),mission.userId())).thenReturn(Optional.of(mission));
        var action=new NextLearningAction(LearningActionType.UNDERSTAND,mission.objectives().getFirst().id(),"topic:"+mission.topicId(),
            LearningDifficulty.FOUNDATIONAL,"INITIAL",true,LearningActionConstraints.unconstrained());
        var frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var receipt=com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt.ready(mission.userId(),UUID.randomUUID(),"a".repeat(64),mission.id(),
            mission.objectives().getFirst().id(),frozen,"b".repeat(64),mission.updatedAt()).claim(UUID.randomUUID(),
            new com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Snapshot(mission.updatedAt(),null,0,0,mission.objectives().getFirst().id(),frozen),action,mission.updatedAt());
        var useCase=new MaterializeLearningActivityUseCase(()->new AuthenticatedUser(mission.userId()),repository,evidence,ai,persistence,
            Clock.fixed(mission.updatedAt(),ZoneOffset.UTC),org.mockito.Mockito.mock(AssessmentAvailabilityResolver.class));
        var budget=java.time.Duration.ofSeconds(150).toNanos();
        var deadline=new ActivityAiTaskPort.PreparationDeadline(System.nanoTime()-(expired?java.time.Duration.ofSeconds(151).toNanos():0),budget);
        assertThatThrownBy(()->useCase.execute(new MaterializeLearningActivityUseCase.Command(mission.id(),action,null,null,receipt.context(),deadline)))
            .isInstanceOf(expired?java.util.concurrent.CancellationException.class:IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(evidence,ai,persistence);
    }

    @BeforeEach
    void setUp() {
        // These mapping/continuation fixtures are noninitial; no attempt, grade or competence evidence is seeded.
        LearningActivity predecessor = new LearningActivity(UUID.randomUUID(), OBJECTIVE_ID,
                LearningActivityType.REFLECT, LearningActionType.REFLECT, null, null, "COMPLETED", null, 1,
                null, false, NOW.minusSeconds(40), NOW.minusSeconds(20), NOW.minusSeconds(50), Set.of());
        missions = new InMemoryMissionRepository(activeMission(List.of(predecessor), predecessor.id()));
        evidence = new RecordingEvidencePort();
        ai = new RecordingAiPort();
        artifacts = new InMemoryArtifactRepository();
        deniedSources = new java.util.HashSet<>();
        var sourceAuthorization = new com.hippocampus.learning.port.ActivitySourceReferenceAuthorization() {
            @Override public boolean allOwnedAndWithinMissionScope(UUID userId, List<MissionMaterial> scopes,
                    Set<UUID> ids) { return ids.stream().noneMatch(deniedSources::contains); }
            @Override public boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> scopes,
                    Set<UUID> ids, boolean required) {
                return (!required || !ids.isEmpty()) && allOwnedAndWithinMissionScope(userId, scopes, ids);
            }
            @Override public boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> scopes,
                    Set<UUID> ids, boolean required,List<MissionMaterial> consulted,Set<UUID> consultedIds) {
                return new java.util.HashSet<>(scopes).containsAll(consulted)
                        && lockAndValidateForCompletion(userId,scopes,ids,required)
                        && allOwnedAndWithinMissionScope(userId,scopes,consultedIds);
            }
        };
        var contracts = org.mockito.Mockito.mock(com.hippocampus.learning.port.ActivityResponseContractRepository.class);
        org.mockito.Mockito.when(contracts.classify(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.hippocampus.learning.port.ActivityResponseContractRepository.ActivityContractKind.PRESENTATION);
        var attempts = org.mockito.Mockito.mock(com.hippocampus.progress.port.StudentAttemptRepository.class);
        var sourcePresentation = (com.hippocampus.learning.port.StudyMissionSourcePresentationRepository)
                (owner, scopes, ids) -> java.util.Optional.of(java.util.List.of());
        var eligibility = new AttemptEvidenceEligibility(contracts,sourceAuthorization,artifacts);
        var assembler = new StudyMissionLearningStateAssembler(eligibility);
        var availability = new AssessmentAvailabilityResolver(
                new JsonActivityResponseContractRepository(artifacts,new ObjectMapper()),
                attempts,sourcePresentation,artifacts,eligibility);
        var durations = new java.util.EnumMap<LearningActionType,Integer>(LearningActionType.class);
        for (var type:LearningActionType.values()) durations.put(type,2);
        var engine = new com.hippocampus.learning.domain.LearningEngine(new com.hippocampus.learning.domain.LearningPolicyConfiguration(
                2,3,4,5,2,com.hippocampus.learning.domain.LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,durations));
        var admission = new StudyMissionContinuationDecision(availability,engine,assembler,sourcePresentation);
        var persistence = new PersistMaterializedActivity(missions,artifacts,sourceAuthorization,null,null,contracts,admission);
        useCase = new MaterializeLearningActivityUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, evidence, ai,
                persistence, Clock.fixed(NOW, ZoneOffset.UTC), availability);
    }

    @Test
    void understandAiActionRetrievesEvidenceInvokesAiAndPersistsValidatedArtifact() {
        var result = execute(action(LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true));

        assertThat(evidence.calls).isOne();
        assertThat(ai.calls).isOne();
        assertThat(ai.lastRequest.evidence().sourceReferenceIds()).containsExactly(SOURCE_ID);
        assertThat(evidence.lastRequest.materialScopes()).containsExactlyElementsOf(
                missions.initial.materials());
        assertThat(result.activity().activityType()).isEqualTo(LearningActivityType.UNDERSTAND);
        assertThat(result.activity().representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(result.activity().generatedArtifactId()).isNotNull();
        assertThat(result.activity().sourceReferenceIds()).containsExactly(SOURCE_ID);
        assertThat(artifacts.saved).hasSize(1);
        assertThat(artifacts.saved.getFirst().userId()).isEqualTo(USER_ID);
        assertThat(artifacts.links.get(result.activity().generatedArtifactId()))
                .containsExactly(SOURCE_ID);
    }

    @Test
    void expectedCurrentActivityMismatchFailsBeforeMaterialization() {
        LearningActivity completed = execute(action(
                LearningActionType.FEEDBACK, null, false, LearningActionConstraints.unconstrained())).activity();
        completeCurrentActivity();

        assertThatThrownBy(() -> useCase.execute(
                new MaterializeLearningActivityUseCase.Command(
                        MISSION_ID,
                        action(LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true),
                        UUID.randomUUID())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION);
        assertThat(missions.current.currentActivityId()).isEqualTo(completed.id());
        assertThat(evidence.calls).isZero();
        assertThat(ai.calls).isZero();
    }

    @Test
    void retrieveAiActionPreservesDifficultyAndSourceRequirement() {
        var constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                false,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD,
                RetrievalActivityType.SHORT_ANSWER);
        var result = execute(action(
                LearningActionType.RETRIEVE,
                LearningDifficulty.APPLIED,
                true,
                constraints));

        assertThat(result.activity().activityType()).isEqualTo(LearningActivityType.RETRIEVE);
        assertThat(result.activity().difficulty()).isEqualTo(LearningDifficulty.APPLIED);
        assertThat(result.activity().sourceRequired()).isTrue();
    }

    @Test
    void understandingCheckUsesRetrieveRenderingAndPersistsResolvableQuestionContract() {
        ai.contentPayload = """
                {"question":"Why does cardiac output rise?","expectedAnswer":"Heart rate or stroke volume rises.","expectedConcepts":["cardiac output"]}
                """;
        var constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false, null, null,
                LearningActivityIntent.STANDARD, RetrievalActivityType.SHORT_ANSWER);

        LearningActivity activity = execute(action(
                LearningActionType.UNDERSTANDING_CHECK,
                LearningDifficulty.FOUNDATIONAL,
                true,
                constraints)).activity();

        assertThat(activity.activityType()).isEqualTo(LearningActivityType.RETRIEVE);
        assertThat(activity.representedActionType())
                .isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
        assertThat(ai.lastRequest.constraints().retrievalActivityType())
                .isEqualTo(RetrievalActivityType.SHORT_ANSWER);
        var contracts = new JsonActivityResponseContractRepository(artifacts, new ObjectMapper());
        var contract = contracts.findValidatedForActivity(
                activity.id(), activity.generatedArtifactId(), USER_ID).orElseThrow();
        assertThat(contract.question()).isEqualTo("Why does cardiac output rise?");
        assertThat(contract.expectedAnswer())
                .isEqualTo("Heart rate or stroke volume rises.");
    }

    @Test
    void connectAiActionProducesConnectActivity() {
        assertThat(execute(action(LearningActionType.CONNECT, LearningDifficulty.INTERMEDIATE, true))
                .activity().activityType()).isEqualTo(LearningActivityType.CONNECT);
    }

    @Test
    void applyAiActionProducesApplyActivity() {
        assertThat(execute(action(LearningActionType.APPLY, LearningDifficulty.APPLIED, true))
                .activity().activityType()).isEqualTo(LearningActivityType.APPLY);
    }

    @Test
    void visualRequiredEducationalActionProducesVisualActivity() {
        var constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, true, false, null, null,
                LearningActivityIntent.STANDARD);
        LearningActivity activity = execute(action(
                LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true, constraints)).activity();
        assertThat(activity.activityType()).isEqualTo(LearningActivityType.VISUAL);
        assertThat(activity.representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
    }

    @Test
    void requiredSourceWithEmptyEvidenceRejectsBeforeAi() {
        evidence.sourceReferenceIds = Set.of();

        assertThatThrownBy(() -> execute(action(
                LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
        assertThat(ai.calls).isZero();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void visualRequiredWithEmptyEvidenceRejectsBeforeAi() {
        evidence.sourceReferenceIds = Set.of();
        var constraints = new LearningActionConstraints(
                SourceRequirement.PREFERRED, true, false, null, null,
                LearningActivityIntent.STANDARD);

        assertThatThrownBy(() -> execute(action(
                LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true, constraints)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
        assertThat(ai.calls).isZero();
    }

    @Test
    void feedbackAndReflectAreDeterministicWithoutEvidenceAiOrArtifact() {
        var feedback = execute(action(LearningActionType.FEEDBACK, null, false,
                LearningActionConstraints.unconstrained()));
        completeCurrentActivity();
        var reflection = execute(action(LearningActionType.REFLECT, null, false,
                LearningActionConstraints.unconstrained()));

        assertThat(feedback.activity().activityType()).isEqualTo(LearningActivityType.FEEDBACK);
        assertThat(reflection.activity().activityType()).isEqualTo(LearningActivityType.REFLECT);
        assertThat(feedback.activity().generatedArtifactId()).isNull();
        assertThat(reflection.activity().generatedArtifactId()).isNull();
        assertThat(feedback.activity().sourceRequired()).isFalse();
        assertThat(reflection.activity().sourceRequired()).isFalse();
        assertThat(feedback.activity().sourceReferenceIds()).isEmpty();
        assertThat(reflection.activity().sourceReferenceIds()).isEmpty();
        assertThat(evidence.calls).isZero();
        assertThat(ai.calls).isZero();
        assertThat(artifacts.saved).isEmpty();
    }

    @Test
    void sourceRequiredDeterministicActivityCannotBypassFinalAuthorization() {
        for (LearningActionType type : List.of(LearningActionType.FEEDBACK, LearningActionType.REFLECT)) {
            assertThatThrownBy(() -> execute(action(type, null, false)))
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(error -> ((ActivityMaterializationException) error).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
        }
        assertThat(missions.saveCalls).isZero();
        assertThat(evidence.calls).isZero();
        assertThat(ai.calls).isZero();
        assertThat(artifacts.saved).isEmpty();
    }

    @Test
    void hintAndPrerequisiteSupportAreAiBackedUnderstandActivities() {
        var hint = execute(action(LearningActionType.HINT, LearningDifficulty.FOUNDATIONAL, true));
        completeCurrentActivity();
        var prerequisite = execute(action(
                LearningActionType.PREREQUISITE_SUPPORT, LearningDifficulty.FOUNDATIONAL, true));

        assertThat(hint.activity().activityType()).isEqualTo(LearningActivityType.UNDERSTAND);
        assertThat(hint.activity().representedActionType()).isEqualTo(LearningActionType.HINT);
        assertThat(prerequisite.activity().activityType()).isEqualTo(LearningActivityType.UNDERSTAND);
        assertThat(prerequisite.activity().representedActionType())
                .isEqualTo(LearningActionType.PREREQUISITE_SUPPORT);
        assertThat(evidence.calls).isEqualTo(2);
        assertThat(ai.calls).isEqualTo(2);
    }

    @Test
    void directAndInheritedInteractionMetadataArePersisted() {
        var constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false, "mechanism", "hint-v2",
                LearningActivityIntent.STANDARD);
        LearningActivity hint = execute(action(
                LearningActionType.HINT, LearningDifficulty.FOUNDATIONAL, true, constraints)).activity();
        completeCurrentActivity();

        LearningActivity retried = execute(action(
                LearningActionType.RETRY, LearningDifficulty.APPLIED, false)).activity();

        assertThat(hint.questionIntent()).isEqualTo("mechanism");
        assertThat(hint.templateSignature()).isEqualTo("hint-v2");
        assertThat(retried.representedActionType()).isEqualTo(LearningActionType.HINT);
        assertThat(retried.questionIntent()).isEqualTo("mechanism");
        assertThat(retried.templateSignature()).isEqualTo("hint-v2");

        var reconstructed = hint.toRecentLearningActivity(
                "cardiac-output", MISSION_ID, null, LearningActivityIntent.STANDARD, true);
        assertThat(reconstructed.learningActivityId()).isEqualTo(hint.id());
        assertThat(reconstructed.activityType()).isEqualTo("HINT");
        assertThat(reconstructed.questionIntent()).isEqualTo("mechanism");
        assertThat(reconstructed.templateSignature()).isEqualTo("hint-v2");
    }

    @Test
    void retryCreatesNewActivityAndPreservesArtifactAndSourcesWithoutAi() {
        LearningActivity prior = completedActivity(
                LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE,
                LearningDifficulty.INTERMEDIATE, UUID.randomUUID());
        artifacts.putExisting(prior.generatedArtifactId());
        missions.replace(activeMission(List.of(prior), prior.id()));

        var result = execute(action(LearningActionType.RETRY, LearningDifficulty.APPLIED, false));

        assertThat(result.activity().id()).isNotEqualTo(prior.id());
        assertThat(result.activity().sequenceNumber()).isEqualTo(2);
        assertThat(result.activity().activityType()).isEqualTo(prior.activityType());
        assertThat(result.activity().representedActionType()).isEqualTo(LearningActionType.RETRIEVE);
        assertThat(result.activity().difficulty()).isEqualTo(prior.difficulty());
        assertThat(result.activity().generatedArtifactId()).isEqualTo(prior.generatedArtifactId());
        assertThat(result.activity().sourceReferenceIds()).isEqualTo(prior.sourceReferenceIds());
        assertThat(ai.calls).isZero();
    }

    @Test
    void retryRejectsUnboundAndConflictingArtifactOriginsWithoutWrites() {
        LearningActivity prior = completedActivity(LearningActivityType.RETRIEVE,
                LearningActionType.RETRIEVE, LearningDifficulty.INTERMEDIATE, UUID.randomUUID());
        artifacts.putExisting(prior.generatedArtifactId());
        missions.replace(activeMission(List.of(prior), prior.id()));
        artifacts.origins.remove(prior.generatedArtifactId());

        assertThatThrownBy(() -> execute(action(LearningActionType.RETRY, LearningDifficulty.APPLIED, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        artifacts.origins.put(prior.generatedArtifactId(), new GeneratedArtifactRepository.ObjectiveOrigin(
                prior.generatedArtifactId(), UUID.randomUUID(), MISSION_ID, USER_ID));
        assertThatThrownBy(() -> execute(action(LearningActionType.RETRY, LearningDifficulty.APPLIED, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        artifacts.origins.put(prior.generatedArtifactId(), new GeneratedArtifactRepository.ObjectiveOrigin(
                prior.generatedArtifactId(), OBJECTIVE_ID, UUID.randomUUID(), USER_ID));
        assertThatThrownBy(() -> execute(action(LearningActionType.RETRY, LearningDifficulty.APPLIED, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        assertThat(missions.saveCalls).isZero();
        assertThat(ai.calls).isZero();
    }

    @Test
    void reduceDifficultyUsesActionDifficultyAndPreservesArtifactAndSources() {
        LearningActivity prior = completedActivity(
                LearningActivityType.APPLY, LearningActionType.APPLY,
                LearningDifficulty.APPLIED, UUID.randomUUID());
        artifacts.putExisting(prior.generatedArtifactId());
        missions.replace(activeMission(List.of(prior), prior.id()));

        var result = execute(action(
                LearningActionType.REDUCE_DIFFICULTY, LearningDifficulty.FOUNDATIONAL, false));

        assertThat(result.activity().difficulty()).isEqualTo(LearningDifficulty.FOUNDATIONAL);
        assertThat(result.activity().representedActionType()).isEqualTo(LearningActionType.APPLY);
        assertThat(result.activity().generatedArtifactId()).isEqualTo(prior.generatedArtifactId());
        assertThat(result.activity().sourceReferenceIds()).isEqualTo(prior.sourceReferenceIds());
        assertThat(ai.calls).isZero();
    }

    @Test
    void reuseValidatedContentRequiresArtifactAndDoesNotExecuteProvider() {
        LearningActivity withoutArtifact = completedActivity(
                LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, null);
        LearningActivity withArtifact = new LearningActivity(
                UUID.randomUUID(), OBJECTIVE_ID, LearningActivityType.CONNECT,
                LearningActionType.CONNECT, null, null, "COMPLETED",
                LearningDifficulty.INTERMEDIATE, 2, UUID.randomUUID(), true,
                NOW.minusSeconds(30), NOW.minusSeconds(20), NOW.minusSeconds(40), Set.of(SOURCE_ID));
        artifacts.putExisting(withArtifact.generatedArtifactId());
        missions.replace(activeMission(List.of(withoutArtifact, withArtifact), withArtifact.id()));

        var result = execute(reuseAction(withArtifact.id()));

        assertThat(result.activity().activityType()).isEqualTo(LearningActivityType.CONNECT);
        assertThat(result.activity().generatedArtifactId()).isEqualTo(withArtifact.generatedArtifactId());
        assertThat(result.activity().representedActionType()).isEqualTo(LearningActionType.CONNECT);
        assertThat(ai.calls).isZero();
    }

    @Test
    void reuseUsesExactSelectedActivityIdentity() {
        LearningActivity selected = completedActivity(
                LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE,
                LearningDifficulty.INTERMEDIATE, UUID.randomUUID());
        LearningActivity newer = new LearningActivity(
                UUID.randomUUID(), OBJECTIVE_ID, LearningActivityType.APPLY,
                LearningActionType.APPLY, null, null, "COMPLETED", LearningDifficulty.APPLIED,
                2, UUID.randomUUID(), true, NOW.minusSeconds(20), NOW.minusSeconds(10),
                NOW.minusSeconds(30), Set.of(SOURCE_ID));
        artifacts.putExisting(selected.generatedArtifactId());
        artifacts.putExisting(newer.generatedArtifactId());
        missions.replace(activeMission(List.of(selected, newer), newer.id()));

        LearningActivity result = execute(reuseAction(selected.id())).activity();

        assertThat(result.generatedArtifactId()).isEqualTo(selected.generatedArtifactId());
        assertThat(result.activityType()).isEqualTo(LearningActivityType.RETRIEVE);
        assertThat(result.representedActionType()).isEqualTo(LearningActionType.RETRIEVE);
    }

    @Test
    void foreignAndWrongObjectiveReuseIdsFailClosed() {
        UUID otherObjectiveId = UUID.randomUUID();
        var current = completedActivity(LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, UUID.randomUUID());
        var wrongObjective = new LearningActivity(
                UUID.randomUUID(), otherObjectiveId, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "COMPLETED",
                LearningDifficulty.FOUNDATIONAL, 2, UUID.randomUUID(), true,
                NOW.minusSeconds(40), NOW.minusSeconds(20), NOW.minusSeconds(50), Set.of(SOURCE_ID));
        artifacts.putExisting(wrongObjective.generatedArtifactId());
        var objectives = new ArrayList<>(activeMission(List.of(), null).objectives());
        objectives.add(new LearningObjective(otherObjectiveId, "Other objective", "other", "Other",
                2, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300)));
        missions.replace(activeMission(List.of(current, wrongObjective), current.id(), objectives));
        var before = missions.current;

        // Both objectives exist in the mission; the request targets OBJECTIVE_ID, not the selected activity's objective.
        for (UUID selectedId : List.of(UUID.randomUUID(), wrongObjective.id())) {
            var existingArtifacts = List.copyOf(artifacts.saved);
            var existingLinks = Map.copyOf(artifacts.links);
            assertThatThrownBy(() -> execute(reuseAction(selectedId)))
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND);
            assertThat(missions.current).isSameAs(before);
            assertThat(artifacts.saved).containsExactlyElementsOf(existingArtifacts);
            assertThat(artifacts.links).isEqualTo(existingLinks);
            assertThat(artifacts.saveCalls).isZero();
            assertThat(artifacts.addSourcesCalls).isZero();
            assertThat(missions.saveCalls).isZero();
            assertThat(evidence.calls).isZero();
            assertThat(ai.calls).isZero();
        }
    }

    @Test
    void missingOrNonMissionObjectiveBindingCannotAcquireAvailabilityOrGenerateContent() {
        // Null activity bindings can enter this boundary; non-member nonnull bindings cannot pass StudyMission.
        var invalid = new LearningActivity(UUID.randomUUID(), null, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "COMPLETED", LearningDifficulty.FOUNDATIONAL,
                1, UUID.randomUUID(), true, NOW.minusSeconds(40), NOW.minusSeconds(20),
                NOW.minusSeconds(50), Set.of(SOURCE_ID));
        var valid = new LearningActivity(UUID.randomUUID(), OBJECTIVE_ID, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "COMPLETED", LearningDifficulty.FOUNDATIONAL,
                2, UUID.randomUUID(), true, NOW.minusSeconds(40), NOW.minusSeconds(20),
                NOW.minusSeconds(50), Set.of(SOURCE_ID));
        for (var current : List.of(invalid, valid)) {
            missions.replace(activeMission(List.of(invalid, valid), current.id()));
            var before = missions.current;
            assertThatThrownBy(() -> execute(action(LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true)))
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION);
            assertThat(missions.current).isSameAs(before);
        }
        missions.replace(activeMission(List.of(valid), valid.id()));
        var before = missions.current;
        var allowedShape = reuseAction(valid.id());
        var unknownObjectiveRequest = new NextLearningAction(allowedShape.actionType(), UUID.randomUUID(),
                allowedShape.conceptKey(), allowedShape.difficulty(), allowedShape.rationaleCode(),
                allowedShape.aiTaskRequired(), allowedShape.constraints(), allowedShape.reuseLearningActivityId());
        assertThatThrownBy(() -> execute(unknownObjectiveRequest))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION);
        assertThat(missions.current).isSameAs(before);
        assertThat(evidence.calls).isZero();
        assertThat(ai.calls).isZero();
        assertThat(artifacts.saved).isEmpty();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void reuseRejectsUnusableOrGroundingIncompatibleArtifact() {
        UUID artifactId = UUID.randomUUID();
        LearningActivity prior = completedActivity(
                LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, artifactId);
        missions.replace(activeMission(List.of(prior), prior.id()));
        artifacts.putExisting(artifactId, USER_ID, false, "VALIDATED", "STRICT_SOURCE", Set.of(SOURCE_ID));

        assertThatThrownBy(() -> execute(reuseAction(prior.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);

        artifacts.saved.clear();
        artifacts.links.clear();
        artifacts.putExisting(artifactId, USER_ID, true, "VALIDATED", "GENERAL_KNOWLEDGE", Set.of(SOURCE_ID));
        assertThatThrownBy(() -> execute(reuseAction(prior.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
    }

    @Test
    void reuseValidatesPersistedArtifactProvenanceWithinMissionScope() {
        UUID artifactId = UUID.randomUUID();
        UUID unauthorizedSource = UUID.randomUUID();
        LearningActivity prior = completedActivity(
                LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, artifactId);
        artifacts.putExisting(
                artifactId, USER_ID, true, "VALIDATED", "STRICT_SOURCE", Set.of(unauthorizedSource));
        deniedSources.add(unauthorizedSource);
        missions.replace(activeMission(List.of(prior), prior.id()));

        assertThatThrownBy(() -> execute(reuseAction(prior.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void unsupportedControlActionChangesNoPersistentState() {
        assertThatThrownBy(() -> execute(action(LearningActionType.PAUSE, null, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.NOT_MATERIALIZABLE);
        assertThat(missions.saveCalls).isZero();
        assertThat(artifacts.saved).isEmpty();
    }

    @Test
    void foreignMissionUsesOwnerScopedNotFound() {
        missions.visible = false;
        assertThatThrownBy(() -> execute(action(LearningActionType.FEEDBACK, null, false)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
    }

    @Test
    void objectiveFromAnotherMissionIsRejected() {
        NextLearningAction action = new NextLearningAction(
                LearningActionType.FEEDBACK, UUID.randomUUID(), "other",
                null, "test", false, LearningActionConstraints.unconstrained());
        assertThatThrownBy(() -> execute(action))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION);
    }

    @Test
    void unfinishedCurrentActivityIsRejected() {
        LearningActivity pending = new LearningActivity(
                UUID.randomUUID(), OBJECTIVE_ID, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "PENDING",
                LearningDifficulty.FOUNDATIONAL, 1, null, false, null, null,
                NOW.minusSeconds(10), Set.of());
        missions.replace(activeMission(List.of(pending), pending.id()));

        assertThatThrownBy(() -> execute(action(LearningActionType.FEEDBACK, null, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY);
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void staleWriteSnapshotIsRejectedWithoutPartialArtifactOrActivityWrite() {
        missions.staleOnLock = true;

        assertThatThrownBy(() -> execute(action(
                LearningActionType.UNDERSTAND, LearningDifficulty.FOUNDATIONAL, true)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION);
        assertThat(artifacts.saved).isEmpty();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void missingCompatiblePriorActivityFailsExplicitly() {
        missions.replace(activeMission(List.of(), null));
        assertThatThrownBy(() -> execute(action(LearningActionType.RETRY, null, false)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND);
    }

    @Test
    void reusedArtifactOwnedByAnotherUserFailsClosed() {
        UUID artifactId = UUID.randomUUID();
        LearningActivity prior = completedActivity(
                LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, artifactId);
        artifacts.putExisting(artifactId, UUID.randomUUID());
        missions.replace(activeMission(List.of(prior), prior.id()));
        var before = missions.current;
        var existingArtifacts = List.copyOf(artifacts.saved);
        var existingLinks = Map.copyOf(artifacts.links);

        assertThatThrownBy(() -> execute(reuseAction(prior.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        assertThat(missions.saveCalls).isZero();
        assertThat(missions.current).isSameAs(before);
        assertThat(artifacts.saved).containsExactlyElementsOf(existingArtifacts);
        assertThat(artifacts.links).isEqualTo(existingLinks);
        assertThat(artifacts.saveCalls).isZero();
        assertThat(artifacts.addSourcesCalls).isZero();
        assertThat(evidence.calls).isZero();
        assertThat(ai.calls).isZero();
    }

    private MaterializeLearningActivityUseCase.Result execute(NextLearningAction action) {
        return useCase.execute(new MaterializeLearningActivityUseCase.Command(MISSION_ID, action));
    }

    private static NextLearningAction action(
            LearningActionType type, LearningDifficulty difficulty, boolean aiRequired) {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false, null, null,
                LearningActivityIntent.STANDARD);
        if (type == LearningActionType.APPLY && aiRequired) {
            constraints = constraints.withApplicationActivityLevel(ApplicationActivityLevel.DIRECT);
        }
        return action(type, difficulty, aiRequired, constraints);
    }

    private static NextLearningAction action(
            LearningActionType type,
            LearningDifficulty difficulty,
            boolean aiRequired,
            LearningActionConstraints constraints) {
        return new NextLearningAction(
                type, OBJECTIVE_ID, "cardiac-output", difficulty, "test", aiRequired, constraints);
    }

    private static NextLearningAction reuseAction(UUID activityId) {
        return new NextLearningAction(
                LearningActionType.REUSE_VALIDATED_CONTENT, OBJECTIVE_ID, "cardiac-output",
                LearningDifficulty.INTERMEDIATE, "test", false,
                new LearningActionConstraints(
                        SourceRequirement.REQUIRED, false, false, null, null,
                        LearningActivityIntent.STANDARD),
                activityId);
    }

    private static StudyMission activeMission(List<LearningActivity> activities, UUID currentActivityId) {
        return activeMission(activities, currentActivityId, List.of(new LearningObjective(
                OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))));
    }

    private static StudyMission activeMission(List<LearningActivity> activities, UUID currentActivityId,
            List<LearningObjective> objectives) {
        return new StudyMission(
                MISSION_ID, USER_ID, TOPIC_ID, null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, currentActivityId,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                objectives,
                activities, NOW.minusSeconds(300), NOW.minusSeconds(60));
    }

    private static LearningActivity completedActivity(
            LearningActivityType type,
            LearningActionType representedActionType,
            LearningDifficulty difficulty,
            UUID artifactId) {
        return new LearningActivity(
                UUID.randomUUID(), OBJECTIVE_ID, type, representedActionType,
                null, null, "COMPLETED", difficulty, 1,
                artifactId, true, NOW.minusSeconds(40), NOW.minusSeconds(20),
                NOW.minusSeconds(50), Set.of(SOURCE_ID));
    }

    private void completeCurrentActivity() {
        StudyMission mission = missions.current;
        LearningActivity current = mission.activities().getLast();
        LearningActivity completed = new LearningActivity(
                current.id(), current.learningObjectiveId(), current.activityType(),
                current.representedActionType(), current.questionIntent(), current.templateSignature(),
                "COMPLETED",
                current.difficulty(), current.sequenceNumber(), current.generatedArtifactId(),
                current.sourceRequired(), NOW.minusSeconds(5), NOW.minusSeconds(1),
                current.createdAt(), current.sourceReferenceIds());
        var activities = new ArrayList<>(mission.activities());
        activities.set(activities.size() - 1, completed);
        missions.replace(new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(),
                mission.status(), mission.learningState(), mission.groundingMode(),
                mission.availableTimeMinutes(), mission.startedAt(), mission.completedAt(),
                mission.stoppedAt(), completed.id(), mission.materials(), mission.objectives(),
                activities, mission.createdAt(), mission.updatedAt()));
    }

    private static final class RecordingEvidencePort implements ActivityEvidencePort {
        int calls;
        Request lastRequest;
        Set<UUID> sourceReferenceIds = Set.of(SOURCE_ID);

        @Override
        public Evidence retrieve(Request request) {
            calls++;
            lastRequest = request;
            return new Evidence(sourceReferenceIds);
        }
    }

    private static final class RecordingAiPort implements ActivityAiTaskPort {
        int calls;
        Request lastRequest;
        String contentPayload;

        @Override
        public ValidatedContent execute(Request request) {
            calls++;
            lastRequest = request;
            return new ValidatedContent(
                    request.actionType() == LearningActionType.UNDERSTANDING_CHECK
                            ? "QUESTION" : "EXPLANATION",
                    request.actionType().name(), "Validated content", contentPayload,
                    request.groundingMode().name(), "SOURCE_GROUNDED_GENERATED",
                    "activity-materialization", "1", "GEMINI", "test-model", null,
                    ValidationStatus.VALIDATED, true);
        }
    }

    private static final class InMemoryMissionRepository implements StudyMissionRepository {
        private final StudyMission initial;
        private StudyMission current;
        private boolean visible = true;
        private boolean staleOnLock;
        private int saveCalls;

        private InMemoryMissionRepository(StudyMission mission) {
            initial = mission;
            current = mission;
        }

        void replace(StudyMission mission) {
            current = mission;
        }

        @Override
        public StudyMission save(StudyMission mission) {
            saveCalls++;
            current = mission;
            return mission;
        }

        @Override
        public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
            return visible && current.id().equals(missionId) && current.userId().equals(ownerId)
                    ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
            if (staleOnLock) {
                current = new StudyMission(
                        current.id(), current.userId(), current.topicId(), current.subtopicId(),
                        current.status(), current.learningState(), current.groundingMode(),
                        current.availableTimeMinutes(), current.startedAt(), current.completedAt(),
                        current.stoppedAt(), current.currentActivityId(), current.materials(),
                        current.objectives(), current.activities(), current.createdAt(),
                        current.updatedAt().plusSeconds(1));
            }
            return findOwnedById(missionId, ownerId);
        }
    }

    private static final class InMemoryArtifactRepository implements GeneratedArtifactRepository {
        private final List<GeneratedArtifact> saved = new ArrayList<>();
        private final Map<UUID, Set<UUID>> links = new LinkedHashMap<>();
        private final Map<UUID, ObjectiveOrigin> origins = new LinkedHashMap<>();
        private int saveCalls;
        private int addSourcesCalls;

        void putExisting(UUID artifactId) {
            putExisting(artifactId, USER_ID);
        }

        void putExisting(UUID artifactId, UUID ownerId) {
            putExisting(artifactId, ownerId, true, "VALIDATED", "STRICT_SOURCE", Set.of(SOURCE_ID));
        }

        void putExisting(
                UUID artifactId,
                UUID ownerId,
                boolean reusable,
                String validationStatus,
                String groundingMode,
                Set<UUID> sourceIds) {
            saved.add(new GeneratedArtifact(
                    artifactId, ownerId, "EXPLANATION", "EXPLANATION", "Existing", null,
                    groundingMode, "SOURCE_GROUNDED_GENERATED", "EXPLANATION_V1", "1",
                    "GEMINI", "test-model", null, validationStatus, reusable, NOW.minusSeconds(100)));
            links.put(artifactId, Set.copyOf(sourceIds));
            origins.put(artifactId, new ObjectiveOrigin(artifactId, OBJECTIVE_ID, MISSION_ID, ownerId));
        }

        @Override
        public GeneratedArtifact save(GeneratedArtifact artifact) {
            saveCalls++;
            saved.add(artifact);
            return artifact;
        }

        @Override
        public void addSources(UUID artifactId, Set<UUID> sourceReferenceIds) {
            addSourcesCalls++;
            links.put(artifactId, Set.copyOf(sourceReferenceIds));
        }

        @Override
        public Optional<GeneratedArtifact> findById(UUID artifactId) {
            return saved.stream().filter(artifact -> artifact.id().equals(artifactId)).findFirst();
        }

        @Override
        public Set<UUID> findSourceReferenceIds(UUID artifactId) {
            return links.getOrDefault(artifactId, Set.of());
        }

        @Override public void bindObjectiveOrigin(ObjectiveOrigin origin) {
            if (origins.putIfAbsent(origin.artifactId(), origin) != null)
                throw new IllegalStateException("origin already bound");
        }

        @Override public Optional<ObjectiveOrigin> findObjectiveOrigin(UUID artifactId, UUID ownerId) {
            return Optional.ofNullable(origins.get(artifactId)).filter(origin -> ownerId.equals(origin.userId()));
        }
    }
}
