package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.bootstrap.JacksonGeneratedActivityContentDecoder;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

import tools.jackson.databind.ObjectMapper;

class GetStudyMissionUseCaseTests {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID ARTIFACT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    private InMemoryMissions missions;
    private InMemoryArtifacts artifacts;
    private StubSourcePresentations sources;
    private GetStudyMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        missions = new InMemoryMissions(mission(null));
        artifacts = new InMemoryArtifacts();
        sources = new StubSourcePresentations();
        useCase = new GetStudyMissionUseCase(
                () -> new AuthenticatedUser(OWNER_ID), missions, artifacts,
                new JacksonGeneratedActivityContentDecoder(new ObjectMapper()), sources);
    }

    @Test
    void ownerReadsMissionWithoutCurrentActivityAndAnotherUserFailsClosed() {
        GetStudyMissionUseCase.Result result = useCase.execute(MISSION_ID);

        assertThat(result.id()).isEqualTo(MISSION_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.stage()).isEqualTo("RETRIEVAL");
        assertThat(result.currentActivity()).isNull();

        missions.visible = false;
        assertThatThrownBy(() -> useCase.execute(MISSION_ID))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
    }

    @Test
    void mapsValidatedRetrievalToExplicitSafeContractWithoutAnswerFields() {
        String payload = """
                {"activityType":"MCQ","concept":"Brachial plexus","learningObjective":"Recall roots",
                 "question":"Which roots form the upper trunk?",
                 "options":[{"id":"A","text":"C5-C6"},{"id":"B","text":"C7-C8"}],
                 "correctOption":"A","expectedAnswer":"C5-C6","explanation":"The upper trunk is C5-C6.",
                 "difficulty":"FOUNDATIONAL","sourceReferences":["ignored-provider-reference"],
                 "limitations":[]}
                """;
        missions.current = mission(activity(LearningActivityType.RETRIEVE));
        artifacts.put(artifact("QUESTION", "QUESTION_GENERATION", payload));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.CurrentActivity activity = useCase.execute(MISSION_ID).currentActivity();

        assertThat(activity.type()).isEqualTo("RETRIEVAL");
        assertThat(activity.classification()).isEqualTo("SOURCE_GROUNDED_GENERATED");
        assertThat(activity.content()).isInstanceOf(GetStudyMissionUseCase.RetrievalContent.class);
        GetStudyMissionUseCase.RetrievalContent content =
                (GetStudyMissionUseCase.RetrievalContent) activity.content();
        assertThat(content.subtype()).isEqualTo("MCQ");
        assertThat(content.question()).contains("upper trunk");
        assertThat(content.options()).extracting(GetStudyMissionUseCase.Option::id)
                .containsExactly("A", "B");
        assertThat(content.toString()).doesNotContain("expectedAnswer", "correctOption", "C5-C6.");
        assertThat(activity.sources()).singleElement().satisfies(source -> {
            assertThat(source.sourceReferenceId()).isEqualTo(SOURCE_ID);
            assertThat(source.materialTitle()).isEqualTo("Upper Limb Lecture");
            assertThat(source.pageNumber()).isEqualTo(14);
        });
    }

    @Test
    void mapsValidatedExplanationToExplicitSafeContract() {
        String payload = """
                {"concept":"Brachial plexus","explanation":"The plexus supplies the upper limb.",
                 "keyPoints":["Roots form trunks"],"prerequisitesUsed":["Spinal nerves"],
                 "sourceReferences":["ignored-provider-reference"],
                 "supplementalKnowledgeUsed":false,"limitations":[]}
                """;
        missions.current = mission(activity(LearningActivityType.UNDERSTAND));
        artifacts.put(artifact("EXPLANATION", "EXPLANATION", payload));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.ExplanationContent content =
                (GetStudyMissionUseCase.ExplanationContent) useCase.execute(MISSION_ID)
                        .currentActivity().content();

        assertThat(content.concept()).isEqualTo("Brachial plexus");
        assertThat(content.keyPoints()).containsExactly("Roots form trunks");
        assertThat(content.toString()).doesNotContain(
                "prerequisitesUsed", "ignored-provider-reference", "supplementalKnowledgeUsed");
    }

    @Test
    void mapsValidatedConnectionToExplicitSafeContract() {
        String payload = """
                {"fromConcept":"Upper trunk","toConcept":"Erb palsy",
                 "relationshipType":"CLINICAL_CORRELATION",
                 "relationship":"An upper-trunk lesion produces the characteristic deficit.",
                 "whyItMatters":"It localizes the lesion.",
                 "sourceReferences":["ignored-provider-reference"],"limitations":[]}
                """;
        missions.current = mission(activity(LearningActivityType.CONNECT));
        artifacts.put(artifact("CONCEPT_CONNECTION", "CONCEPT_CONNECTION", payload));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.ConnectionContent content =
                (GetStudyMissionUseCase.ConnectionContent) useCase.execute(MISSION_ID)
                        .currentActivity().content();

        assertThat(content.fromConcept()).isEqualTo("Upper trunk");
        assertThat(content.toConcept()).isEqualTo("Erb palsy");
        assertThat(content.toString()).doesNotContain("ignored-provider-reference");
    }

    @Test
    void applicationPresentationWithholdsExpectedAnswerReasoningAndFeedbackPoints() {
        String payload = """
                {"scenario":"A learner reviews an upper limb lesion.","question":"Which trunk is involved?",
                 "targetConcept":"Upper trunk","requiredReasoning":["PRIVATE_REASONING"],
                 "expectedAnswer":"PRIVATE_ANSWER","feedbackPoints":["PRIVATE_FEEDBACK"],
                 "difficulty":"FOUNDATIONAL_APPLIED","sourceReferences":[],"limitations":["Educational only"]}
                """;
        missions.current = mission(activity(LearningActivityType.APPLY));
        artifacts.put(artifact("CONTEXTUAL_APPLICATION", "CONTEXTUAL_APPLICATION", payload));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.ApplicationContent content =
                (GetStudyMissionUseCase.ApplicationContent) useCase.execute(MISSION_ID)
                        .currentActivity().content();

        assertThat(content.scenario()).contains("upper limb");
        assertThat(content.toString()).doesNotContain(
                "PRIVATE_REASONING", "PRIVATE_ANSWER", "PRIVATE_FEEDBACK");
    }

    @Test
    void malformedMismatchedOrUnauthorizedArtifactFailsClosed() {
        missions.current = mission(activity(LearningActivityType.RETRIEVE));
        artifacts.put(artifact("QUESTION", "QUESTION_GENERATION", "{not-json"));
        artifacts.sources = Set.of(SOURCE_ID);

        assertNotFound();

        artifacts.put(artifact("EXPLANATION", "EXPLANATION", "{}"));
        assertNotFound();

        artifacts.visible = false;
        assertNotFound();
    }

    @Test
    void unauthorizedOrStaleSourcePresentationFailsClosed() {
        missions.current = mission(activity(LearningActivityType.VISUAL, null));
        sources.authorized = false;

        assertNotFound();
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> useCase.execute(MISSION_ID))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
    }

    private static StudyMission mission(LearningActivity activity) {
        List<LearningActivity> activities = activity == null ? List.of() : List.of(activity);
        return new StudyMission(
                MISSION_ID, OWNER_ID, UUID.randomUUID(), null, StudyMissionStatus.ACTIVE,
                LearningStage.RETRIEVAL, StudyMissionGroundingMode.STRICT_SOURCE, 25,
                NOW.minusSeconds(600), null, null, activity == null ? null : activity.id(),
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                List.of(), activities, NOW.minusSeconds(700), NOW);
    }

    private static LearningActivity activity(LearningActivityType type) {
        return activity(type, ARTIFACT_ID);
    }

    private static LearningActivity activity(LearningActivityType type, UUID artifactId) {
        LearningActionType action = switch (type) {
            case UNDERSTAND -> LearningActionType.UNDERSTAND;
            case RETRIEVE -> LearningActionType.RETRIEVE;
            case CONNECT -> LearningActionType.CONNECT;
            case APPLY -> LearningActionType.APPLY;
            case VISUAL -> LearningActionType.SOURCE_ONLY;
            case FEEDBACK -> LearningActionType.FEEDBACK;
            case REFLECT -> LearningActionType.REFLECT;
        };
        return new LearningActivity(
                ACTIVITY_ID, null, type, action, null, null, "ACTIVE",
                LearningDifficulty.FOUNDATIONAL, 1, artifactId, true,
                NOW.minusSeconds(60), null, NOW.minusSeconds(120), Set.of(SOURCE_ID));
    }

    private static GeneratedArtifactRepository.GeneratedArtifact artifact(
            String artifactType, String taskType, String payload) {
        return new GeneratedArtifactRepository.GeneratedArtifact(
                ARTIFACT_ID, OWNER_ID, artifactType, taskType, "learner content", payload,
                "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", "prompt", "1",
                "PRIVATE_PROVIDER", "PRIVATE_MODEL", "PRIVATE_MODEL_VERSION",
                "VALIDATED", true, NOW);
    }

    private static final class InMemoryMissions implements StudyMissionRepository {
        private StudyMission current;
        private boolean visible = true;

        private InMemoryMissions(StudyMission current) {
            this.current = current;
        }

        @Override public StudyMission save(StudyMission mission) { current = mission; return mission; }
        @Override public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
            return visible && current.id().equals(missionId) && current.userId().equals(ownerId)
                    ? Optional.of(current) : Optional.empty();
        }
        @Override public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
            return findOwnedById(missionId, ownerId);
        }
    }

    private static final class InMemoryArtifacts implements GeneratedArtifactRepository {
        private final Map<UUID, GeneratedArtifact> values = new HashMap<>();
        private Set<UUID> sources = Set.of();
        private boolean visible = true;

        void put(GeneratedArtifact artifact) { values.put(artifact.id(), artifact); }
        @Override public GeneratedArtifact save(GeneratedArtifact artifact) { put(artifact); return artifact; }
        @Override public void addSources(UUID artifactId, Set<UUID> sourceReferenceIds) {
            sources = Set.copyOf(sourceReferenceIds);
        }
        @Override public Optional<GeneratedArtifact> findById(UUID artifactId) {
            return Optional.ofNullable(values.get(artifactId));
        }
        @Override public Optional<GeneratedArtifact> findOwnedById(UUID artifactId, UUID ownerId) {
            return visible ? GeneratedArtifactRepository.super.findOwnedById(artifactId, ownerId)
                    : Optional.empty();
        }
        @Override public Set<UUID> findSourceReferenceIds(UUID artifactId) { return sources; }
    }

    private static final class StubSourcePresentations
            implements StudyMissionSourcePresentationRepository {
        private boolean authorized = true;

        @Override
        public Optional<List<SourcePresentation>> resolveAuthorized(
                UUID ownerId, List<MissionMaterial> missionMaterials, Set<UUID> sourceReferenceIds) {
            return authorized
                    ? Optional.of(List.of(new SourcePresentation(
                            SOURCE_ID, "Upper Limb Lecture", 14, "Posterior Cord")))
                    : Optional.empty();
        }
    }
}
