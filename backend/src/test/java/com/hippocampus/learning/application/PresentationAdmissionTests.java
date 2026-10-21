package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.port.StudentAttemptRepository;
import tools.jackson.databind.ObjectMapper;

class PresentationAdmissionTests {
    private final UUID owner = UUID.randomUUID(), artifactId = UUID.randomUUID(), source = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-17T00:00:00Z");
    private final GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
    private final StudyMissionRepository missions = mock(StudyMissionRepository.class);
    private final StudentAttemptRepository attempts = mock(StudentAttemptRepository.class);
    private final MaterializeLearningActivityUseCase materializer = mock(MaterializeLearningActivityUseCase.class);
    private final ActivitySourceReferenceAuthorization sources = mock(ActivitySourceReferenceAuthorization.class);
    private final StudyMissionSourcePresentationRepository presentationSources = mock(StudyMissionSourcePresentationRepository.class);
    private final LearningEngine engine = mock(LearningEngine.class);
    private final JsonActivityResponseContractRepository contracts =
            new JsonActivityResponseContractRepository(artifacts, new ObjectMapper());
    private final StudyMissionContinuationDecision decision = spy(new StudyMissionContinuationDecision(
            new AssessmentAvailabilityResolver(contracts, attempts, presentationSources, artifacts), engine,
            new StudyMissionLearningStateAssembler(), presentationSources));
    private static final String EXPLANATION = """
            {"concept":"Synthetic","explanation":"Synthetic explanation","keyPoints":[],
             "prerequisitesUsed":[],"sourceReferences":[],"supplementalKnowledgeUsed":false,"limitations":[]}
            """;
    private static final String CONNECTION = """
            {"fromConcept":"First","toConcept":"Second","relationshipType":"Synthetic",
             "relationship":"Synthetic relationship","whyItMatters":"Synthetic context",
             "sourceReferences":[],"limitations":[]}
            """;

    @Test
    void invalidArtifactsCannotCausePresentationLifecycleOrOtherWrites() {
        for (String invalid : List.of("missing", "foreign", "unvalidated", "malformed", "incomplete", "null-payload",
                "binding", "type", "version", "grounding", "response")) {
            reset(artifacts, missions, decision, materializer, sources, attempts, engine, presentationSources);
            StudyMission mission = mission(LearningActionType.UNDERSTAND);
            var valid = artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2",
                    "STRICT_SOURCE", EXPLANATION);
            var value = switch (invalid) {
                case "foreign" -> artifact(UUID.randomUUID(), "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION);
                case "unvalidated" -> artifact(owner, "REJECTED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION);
                case "malformed" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", "{");
                case "incomplete" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", "{}");
                case "null-payload" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", null);
                case "type" -> artifact(owner, "VALIDATED", "QUESTION", "QUESTION_GENERATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION);
                case "version" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "1", "STRICT_SOURCE", EXPLANATION);
                case "grounding" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "GENERAL", EXPLANATION);
                case "response" -> artifact(owner, "VALIDATED", "EXPLANATION", "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION.replace("\"limitations\":[]", "\"limitations\":[],\"question\":\"Answer?\",\"expectedAnswer\":\"Answer\""));
                default -> valid;
            };
            when(artifacts.findById(artifactId)).thenReturn("missing".equals(invalid) ? Optional.empty() : Optional.of(value));
            when(artifacts.findSourceReferenceIds(artifactId)).thenReturn("binding".equals(invalid) ? Set.of(UUID.randomUUID()) : Set.of(source));
            when(missions.findOwnedById(mission.id(), owner)).thenReturn(Optional.of(mission));
            when(missions.findOwnedByIdForUpdate(mission.id(), owner)).thenReturn(Optional.of(mission));
            var persistence = presentationPersistence();
            var useCase = new ContinueStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions, contracts,
                    decision, materializer, persistence, mock(ChangeStudyMissionStatusUseCase.class), Clock.fixed(now, ZoneOffset.UTC));
            assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(mission.id(), mission.currentActivityId(), true)))
                    .as(invalid).isInstanceOf(ActivityMaterializationException.class)
                    .extracting(e -> ((ActivityMaterializationException) e).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
            // The write operation itself also refuses invalid contracts, independently of Continue.
            assertThatThrownBy(() -> persistence.persist(new PersistPresentationCompletion.Command(
                    mission.id(), owner, mission.currentActivityId(), mission.updatedAt(), now)))
                    .as(invalid).isInstanceOf(ActivityMaterializationException.class);
            verify(missions, never()).save(any());
            verify(artifacts, never()).save(any());
            verify(artifacts, never()).addSources(any(), anySet());
            verifyNoInteractions(attempts, materializer, decision, sources, engine, presentationSources);
            assertThat(mission.activities().getFirst().status()).isEqualTo("PENDING");
        }
    }

    @Test
    void validPresentationStillRequiresAuthorizedFrozenSourcesAtTheWriteBoundary() {
        StudyMission mission = mission(LearningActionType.UNDERSTAND);
        when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact(owner, "VALIDATED", "EXPLANATION",
                "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION)));
        when(artifacts.findSourceReferenceIds(artifactId)).thenReturn(Set.of(source));
        when(missions.findOwnedByIdForUpdate(mission.id(), owner)).thenReturn(Optional.of(mission));
        when(sources.lockAndValidateForCompletion(owner, mission.materials(), Set.of(source), true)).thenReturn(false);
        var persistence = presentationPersistence();
        assertThatThrownBy(() -> persistence.persist(new PersistPresentationCompletion.Command(mission.id(), owner,
                mission.currentActivityId(), mission.updatedAt(), now)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(e -> ((ActivityMaterializationException) e).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
        verify(missions, never()).save(any());
        verify(artifacts, never()).save(any());
        verify(artifacts, never()).addSources(any(), anySet());
        verifyNoInteractions(attempts, materializer, decision, engine, presentationSources);
        assertThat(mission.activities().getFirst().status()).isEqualTo("PENDING");
    }

    @Test
    void conflictingStoredOriginCannotAuthorizePresentationCompletion() {
        var mission = mission(LearningActionType.UNDERSTAND);
        when(missions.findOwnedByIdForUpdate(mission.id(), owner)).thenReturn(Optional.of(mission));
        when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact(owner, "VALIDATED", "EXPLANATION",
                "EXPLANATION", "EXPLANATION_V2", "2", "STRICT_SOURCE", EXPLANATION)));
        when(artifacts.findSourceReferenceIds(artifactId)).thenReturn(Set.of(source));
        when(artifacts.findObjectiveOrigin(artifactId, owner)).thenReturn(Optional.of(
                new GeneratedArtifactRepository.ObjectiveOrigin(artifactId, UUID.randomUUID(), mission.id(), owner)));
        var persistence = presentationPersistence();
        assertThatThrownBy(() -> persistence.persist(new PersistPresentationCompletion.Command(mission.id(), owner,
                mission.currentActivityId(), mission.updatedAt(), now)))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        verify(missions, never()).save(any());
        verifyNoInteractions(sources, attempts);
    }

    @Test
    void positivelyValidatedPresentationsCompleteWithoutAttemptsOrProviderCalls() {
        for (var action : List.of(LearningActionType.UNDERSTAND, LearningActionType.HINT,
                LearningActionType.PREREQUISITE_SUPPORT, LearningActionType.CONNECT)) {
            reset(artifacts, missions, decision, materializer, sources, attempts, engine, presentationSources);
            StudyMission mission = mission(action);
            boolean connection = action == LearningActionType.CONNECT;
            when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact(owner, "VALIDATED",
                    connection ? "CONCEPT_CONNECTION" : "EXPLANATION", connection ? "CONCEPT_CONNECTION" : "EXPLANATION",
                    connection ? "CONCEPT_CONNECTION_V1" : "EXPLANATION_V2", connection ? "1" : "2",
                    "STRICT_SOURCE", connection ? CONNECTION : EXPLANATION)));
            when(artifacts.findSourceReferenceIds(artifactId)).thenReturn(Set.of(source));
            when(missions.findOwnedById(mission.id(), owner)).thenReturn(Optional.of(mission));
            when(missions.findOwnedByIdForUpdate(mission.id(), owner)).thenReturn(Optional.of(mission));
            when(missions.save(any())).thenAnswer(call -> call.getArgument(0));
            when(sources.lockAndValidateForCompletion(eq(owner), anyList(), eq(Set.of(source)), eq(true))).thenReturn(true);
            when(presentationSources.resolveAuthorized(eq(owner), eq(mission.materials()), eq(Set.of(source))))
                    .thenReturn(Optional.of(List.of(new StudyMissionSourcePresentationRepository.SourcePresentation(source, "Synthetic", 1, "Synthetic"))));
            when(attempts.findOwnedByActivity(mission.currentActivityId(), owner)).thenReturn(List.of());
            when(engine.decide(any())).thenReturn(new NextLearningAction(LearningActionType.COMMUNICATE_LIMITATION,
                            mission.objectives().getFirst().id(), null, null, "SOURCE_LIMITATION", false));
            var useCase = new ContinueStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions, contracts,
                    decision, materializer, presentationPersistence(),
                    mock(ChangeStudyMissionStatusUseCase.class), Clock.fixed(now, ZoneOffset.UTC));
            assertThat(useCase.execute(new ContinueStudyMissionUseCase.Command(mission.id(), mission.currentActivityId(), true)).outcome()).isEqualTo("NO_ACTIVITY");
            var saved = org.mockito.ArgumentCaptor.forClass(StudyMission.class);
            verify(missions).save(saved.capture());
            assertThat(saved.getValue().activities().getFirst().status()).isEqualTo("COMPLETED");
            verify(attempts, never()).append(any());
            verifyNoInteractions(materializer);
            verify(artifacts, never()).save(any());
            verify(artifacts, never()).addSources(any(), anySet());
        }
    }

    private PersistPresentationCompletion presentationPersistence() {
        when(artifacts.findOwnedByIdForUpdate(artifactId, owner)).thenAnswer(call ->
                artifacts.findById(artifactId).filter(value -> value.userId().equals(owner)));
        when(artifacts.findSourceReferenceIdsForShare(artifactId)).thenAnswer(call ->
                artifacts.findSourceReferenceIds(artifactId));
        return new PersistPresentationCompletion(missions, contracts, sources, artifacts);
    }

    private GeneratedArtifactRepository.GeneratedArtifact artifact(UUID user, String validation, String type,
            String task, String prompt, String version, String grounding, String payload) {
        return new GeneratedArtifactRepository.GeneratedArtifact(artifactId, user, type, task, "Synthetic", payload,
                grounding, "SOURCE_GROUNDED_GENERATED", prompt, version, "test", "test", "test", validation, true, now);
    }

    private StudyMission mission(LearningActionType action) {
        UUID id = UUID.randomUUID(), objective = UUID.randomUUID(), activity = UUID.randomUUID();
        return new StudyMission(id, owner, UUID.randomUUID(), null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 10, now.minusSeconds(60),
                null, null, activity, List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)), List.of(new LearningObjective(objective, "Synthetic", "synthetic", "Synthetic",
                        1, LearningObjectiveStatus.ACTIVE, now.minusSeconds(60))),
                List.of(new LearningActivity(activity, objective, action == LearningActionType.CONNECT ? LearningActivityType.CONNECT : LearningActivityType.UNDERSTAND,
                        action, null, null, "PENDING", LearningDifficulty.FOUNDATIONAL, 1, artifactId, true, null, null, now.minusSeconds(60), Set.of(source))),
                now.minusSeconds(60), now.minusSeconds(60));
    }
}
