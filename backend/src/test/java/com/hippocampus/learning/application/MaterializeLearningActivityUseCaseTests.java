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

    @BeforeEach
    void setUp() {
        missions = new InMemoryMissionRepository(activeMission(List.of(), null));
        evidence = new RecordingEvidencePort();
        ai = new RecordingAiPort();
        artifacts = new InMemoryArtifactRepository();
        deniedSources = new java.util.HashSet<>();
        var persistence = new PersistMaterializedActivity(
                missions, artifacts, (userId, scopes, ids) -> ids.stream().noneMatch(deniedSources::contains));
        useCase = new MaterializeLearningActivityUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, evidence, ai,
                persistence, Clock.fixed(NOW, ZoneOffset.UTC));
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
        var feedback = execute(action(LearningActionType.FEEDBACK, null, false));
        completeCurrentActivity();
        var reflection = execute(action(LearningActionType.REFLECT, null, false));

        assertThat(feedback.activity().activityType()).isEqualTo(LearningActivityType.FEEDBACK);
        assertThat(reflection.activity().activityType()).isEqualTo(LearningActivityType.REFLECT);
        assertThat(feedback.activity().generatedArtifactId()).isNull();
        assertThat(reflection.activity().generatedArtifactId()).isNull();
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
        LearningActivity wrongObjective = new LearningActivity(
                UUID.randomUUID(), null, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "COMPLETED",
                LearningDifficulty.FOUNDATIONAL, 1, UUID.randomUUID(), true,
                NOW.minusSeconds(40), NOW.minusSeconds(20), NOW.minusSeconds(50), Set.of(SOURCE_ID));
        artifacts.putExisting(wrongObjective.generatedArtifactId());
        missions.replace(activeMission(List.of(wrongObjective), wrongObjective.id()));

        assertThatThrownBy(() -> execute(reuseAction(UUID.randomUUID())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND);
        assertThatThrownBy(() -> execute(reuseAction(wrongObjective.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND);
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

        assertThatThrownBy(() -> execute(reuseAction(prior.id())))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        assertThat(missions.saveCalls).isZero();
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
        return new StudyMission(
                MISSION_ID, USER_ID, TOPIC_ID, null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, currentActivityId,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                List.of(new LearningObjective(
                        OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                        1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))),
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

        @Override
        public ValidatedContent execute(Request request) {
            calls++;
            lastRequest = request;
            return new ValidatedContent(
                    "EXPLANATION", request.actionType().name(), "Validated content", null,
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
        }

        @Override
        public GeneratedArtifact save(GeneratedArtifact artifact) {
            saved.add(artifact);
            return artifact;
        }

        @Override
        public void addSources(UUID artifactId, Set<UUID> sourceReferenceIds) {
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
    }
}
