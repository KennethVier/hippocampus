package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.AssessmentAvailability;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

import tools.jackson.databind.ObjectMapper;

class AssessmentAvailabilityCompatibilityTests {
    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");
    private static final String MCQ = """
            {"activityType":"MCQ","concept":"Synthetic","learningObjective":"Recall",
             "question":"Synthetic question?","expectedAnswer":"Synthetic answer",
             "correctOption":"A","options":[{"id":"A","text":"First"},{"id":"B","text":"Second"}],
             "explanation":"Synthetic explanation","difficulty":"FOUNDATIONAL",
             "sourceReferences":[],"limitations":[]}
            """;
    private static final String FREE_TEXT = """
            {"activityType":"SHORT_ANSWER","concept":"Synthetic","learningObjective":"Recall",
             "question":"Synthetic question?","expectedAnswer":"Synthetic answer",
             "correctOption":null,"options":[],"explanation":"Synthetic explanation",
             "difficulty":"FOUNDATIONAL","sourceReferences":[],"limitations":[]}
            """;

    @Test
    void incompatibleSavedContractsCannotAuthorizeRetryOrReusedContent() {
        for (String scenario : List.of("wrong-type", "wrong-version", "unsupported-version",
                "wrong-grounding", "wrong-source-binding", "unauthorized-source",
                "wrong-artifact-association", "malformed", "incomplete", "invalid-mcq",
                "missing-positive-admission", "missing-origin", "wrong-origin")) {
            Fixture fixture = new Fixture(scenario, MCQ);
            var facts = fixture.availability.resolve(fixture.mission, fixture.activity,
                    fixture.availability.history(fixture.mission));
            assertThat(facts.activities()).as(scenario).containsEntry(fixture.activity.id(),
                    AssessmentAvailability.Kind.UNKNOWN);
            assertThatThrownBy(() -> fixture.useCase.execute(
                    new ContinueStudyMissionUseCase.Command(fixture.mission.id(), fixture.activity.id(), true)))
                    .as(scenario)
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(error -> ((ActivityMaterializationException) error).reason())
                    .isEqualTo("unauthorized-source".equals(scenario)
                            ? ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES
                            : ActivityMaterializationException.Reason.INVALID_ARTIFACT);
            for (NextLearningAction action : List.of(fixture.retry(), fixture.reuse())) {
                assertThatThrownBy(() -> fixture.direct.execute(
                        new MaterializeLearningActivityUseCase.Command(fixture.mission.id(),
                                action, fixture.activity.id())))
                        .as(scenario + " " + action.actionType())
                        .isInstanceOf(ActivityMaterializationException.class)
                        .extracting(error -> ((ActivityMaterializationException) error).reason())
                        .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
            }
            verifyNoInteractions(fixture.materializer, fixture.evidence, fixture.ai, fixture.persistence);
            verify(fixture.missions, never()).save(any());
            verify(fixture.attempts, never()).append(any());
            verify(fixture.artifacts, never()).save(any());
            verify(fixture.artifacts, never()).addSources(any(), anySet());
            assertThat(fixture.mission.activities()).containsExactly(fixture.activity);
            assertThat(fixture.attempt.evaluationStatus()).isEqualTo("UNGRADED");
            assertThat(fixture.attempt.deterministicResult()).isNull();
        }
    }

    @Test
    void compatibleMcqRemainsDeterministicAndKnownFreeTextRemainsUnavailable() {
        Fixture mcq = new Fixture("valid", MCQ);
        var mcqFacts = mcq.availability.resolve(mcq.mission, mcq.activity,
                mcq.availability.history(mcq.mission));
        assertThat(mcqFacts.activities()).containsEntry(mcq.activity.id(),
                AssessmentAvailability.Kind.DETERMINISTIC);
        assertThat(mcq.continuation.decide(mcq.mission, mcq.activity, NOW).action().actionType())
                .isEqualTo(LearningActionType.RETRY);

        Fixture freeText = new Fixture("valid", FREE_TEXT);
        var freeTextFacts = freeText.availability.resolve(freeText.mission, freeText.activity,
                freeText.availability.history(freeText.mission));
        assertThat(freeTextFacts.activities()).containsEntry(freeText.activity.id(),
                AssessmentAvailability.Kind.UNAVAILABLE);
        assertThat(freeTextFacts.constrain(freeText.retry()).actionType())
                .isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
    }

    private static final class Fixture {
        final UUID owner = UUID.randomUUID(), objectiveId = UUID.randomUUID(), activityId = UUID.randomUUID();
        final UUID artifactId = UUID.randomUUID(), sourceId = UUID.randomUUID();
        final StudyMissionRepository missions = mock(StudyMissionRepository.class);
        final StudentAttemptRepository attempts = mock(StudentAttemptRepository.class);
        final GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
        final StudyMissionSourcePresentationRepository sources = mock(StudyMissionSourcePresentationRepository.class);
        final ActivityEvidencePort evidence = mock(ActivityEvidencePort.class);
        final ActivityAiTaskPort ai = mock(ActivityAiTaskPort.class);
        final PersistMaterializedActivity persistence = mock(PersistMaterializedActivity.class);
        final MaterializeLearningActivityUseCase materializer = mock(MaterializeLearningActivityUseCase.class);
        final LearningActivity activity;
        final StudyMission mission;
        final StudentAttempt attempt;
        final AssessmentAvailabilityResolver availability;
        final StudyMissionContinuationDecision continuation;
        final ContinueStudyMissionUseCase useCase;
        final MaterializeLearningActivityUseCase direct;

        Fixture(String scenario, String payload) {
            activity = new LearningActivity(activityId, objectiveId, LearningActivityType.RETRIEVE,
                    LearningActionType.RETRIEVE, null, null, "COMPLETED", LearningDifficulty.FOUNDATIONAL,
                    1, artifactId, true, NOW.minusSeconds(30), NOW.minusSeconds(10),
                    NOW.minusSeconds(30), Set.of(sourceId));
            mission = new StudyMission(UUID.randomUUID(), owner, UUID.randomUUID(), null,
                    StudyMissionStatus.ACTIVE, LearningStage.RETRIEVAL, StudyMissionGroundingMode.STRICT_SOURCE,
                    20, NOW.minusSeconds(30), null, null, activityId,
                    List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)),
                    List.of(new LearningObjective(objectiveId, "Synthetic", "synthetic", "Synthetic", 1,
                            LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(30))),
                    List.of(activity), NOW.minusSeconds(30), NOW.minusSeconds(10));
            attempt = new StudentAttempt(UUID.randomUUID(), owner, activityId, 1, "Synthetic response", null,
                    NOW.minusSeconds(10), "UNGRADED", null, null, NOW.minusSeconds(10), UUID.randomUUID(),
                    "EVALUATION_UNRESOLVED", null);
            when(missions.findOwnedById(mission.id(), owner)).thenReturn(Optional.of(mission));
            when(attempts.findOwnedByActivity(activityId, owner)).thenReturn(List.of(attempt));
            String artifactType = "wrong-type".equals(scenario) ? "EXPLANATION" : "QUESTION";
            String taskType = "wrong-type".equals(scenario) ? "EXPLANATION" : "QUESTION_GENERATION";
            String prompt = "unsupported-version".equals(scenario) ? "QUESTION_GENERATION_V3"
                    : "QUESTION_GENERATION_V2";
            String version = "wrong-version".equals(scenario) ? "1" : "2";
            String grounding = "wrong-grounding".equals(scenario) ? "GENERAL" : "STRICT_SOURCE";
            String storedPayload = switch (scenario) {
                case "malformed" -> "{";
                case "incomplete" -> "{\"activityType\":\"MCQ\",\"question\":\"Synthetic question?\",\"correctOption\":\"A\"}";
                case "invalid-mcq" -> MCQ.replace("\"id\":\"A\"", "\"id\":\"C\"");
                default -> payload;
            };
            UUID storedId = "wrong-artifact-association".equals(scenario) ? UUID.randomUUID() : artifactId;
            var artifact = new GeneratedArtifactRepository.GeneratedArtifact(storedId, owner,
                    artifactType, taskType, "Synthetic", storedPayload, grounding,
                    "SOURCE_GROUNDED_GENERATED", prompt, version, "provider", "model", "version",
                    "VALIDATED", true, NOW);
            when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
            when(artifacts.findOwnedById(artifactId,owner)).thenReturn(Optional.of(artifact));
            if (!"missing-origin".equals(scenario)) {
                when(artifacts.findObjectiveOrigin(artifactId, owner)).thenReturn(Optional.of(
                        new GeneratedArtifactRepository.ObjectiveOrigin(artifactId,
                                "wrong-origin".equals(scenario) ? UUID.randomUUID() : objectiveId,
                                mission.id(), owner)));
            }
            when(artifacts.findSourceReferenceIds(artifactId)).thenReturn(
                    "wrong-source-binding".equals(scenario) ? Set.of(UUID.randomUUID()) : Set.of(sourceId));
            when(sources.resolveAuthorized(eq(owner), eq(mission.materials()), eq(Set.of(sourceId))))
                    .thenReturn("unauthorized-source".equals(scenario) ? Optional.empty()
                            : Optional.of(List.of(new StudyMissionSourcePresentationRepository.SourcePresentation(
                                    sourceId, "Synthetic", 1, "Synthetic"))));
            ActivityResponseContractRepository contracts;
            if ("missing-positive-admission".equals(scenario)) {
                contracts = mock(ActivityResponseContractRepository.class);
                when(contracts.findValidatedForActivity(activityId, artifactId, owner)).thenReturn(Optional.of(
                        new ActivityResponseContractRepository.ResponseContract("Synthetic question?", List.of(),
                                "Synthetic answer", "A", "Synthetic feedback")));
            } else {
                contracts = new JsonActivityResponseContractRepository(artifacts, new ObjectMapper());
            }
            var sourceAuthorization = mock(com.hippocampus.learning.port.ActivitySourceReferenceAuthorization.class);
            when(sourceAuthorization.availableForRead(owner,mission.materials(),Set.of(sourceId)))
                    .thenAnswer(call -> sources.resolveAuthorized(owner,mission.materials(),Set.of(sourceId)).isPresent());
            var eligibility = new AttemptEvidenceEligibility(contracts,sourceAuthorization,artifacts);
            var assembler = new StudyMissionLearningStateAssembler(eligibility);
            availability = new AssessmentAvailabilityResolver(contracts, attempts, sources, artifacts,eligibility);
            LearningEngine engine = mock(LearningEngine.class);
            when(engine.decide(any())).thenReturn(retry());
            continuation = new StudyMissionContinuationDecision(availability, engine,
                    assembler, sources);
            useCase = new ContinueStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions,
                    contracts, continuation, materializer, mock(PersistPresentationCompletion.class),
                    mock(ChangeStudyMissionStatusUseCase.class), Clock.fixed(NOW, ZoneOffset.UTC));
            direct = new MaterializeLearningActivityUseCase(() -> new AuthenticatedUser(owner), missions,
                    evidence, ai, persistence, Clock.fixed(NOW, ZoneOffset.UTC), availability);
        }

        NextLearningAction retry() {
            return new NextLearningAction(LearningActionType.RETRY, objectiveId, "synthetic",
                    LearningDifficulty.FOUNDATIONAL, "UNGRADED_RETRY", true);
        }

        NextLearningAction reuse() {
            return new NextLearningAction(LearningActionType.REUSE_VALIDATED_CONTENT, objectiveId, "synthetic",
                    LearningDifficulty.FOUNDATIONAL, "UNGRADED_RETRY", false,
                    LearningActionConstraints.unconstrained(), activityId);
        }
    }
}
