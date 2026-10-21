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
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
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
    void initialRecoveryProjectsExpiredClaimWithoutMutatingReceiptOrMission() {
        var mission=InitialStudyMissionStateAssemblerTests.mission(20);
        var repository=org.mockito.Mockito.mock(StudyMissionRepository.class);
        var receipts=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        var authority=org.mockito.Mockito.mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        var frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var receipt=com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt.ready(mission.userId(),UUID.randomUUID(),"a".repeat(64),
            mission.id(),mission.objectives().getFirst().id(),frozen,"b".repeat(64),mission.updatedAt()).claim(UUID.randomUUID(),
            new com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Snapshot(mission.updatedAt(),null,0,0,mission.objectives().getFirst().id(),frozen),
            new com.hippocampus.learning.domain.NextLearningAction(LearningActionType.UNDERSTAND,mission.objectives().getFirst().id(),"topic:"+mission.topicId(),
                LearningDifficulty.FOUNDATIONAL,"INITIAL",true,com.hippocampus.learning.domain.LearningActionConstraints.unconstrained()),mission.updatedAt());
        org.mockito.Mockito.when(repository.findOwnedById(mission.id(),mission.userId())).thenReturn(Optional.of(mission));
        org.mockito.Mockito.when(receipts.findByMission(mission.userId(),mission.id())).thenReturn(Optional.of(receipt));
        org.mockito.Mockito.when(receipts.databaseTime()).thenReturn(mission.updatedAt().plusSeconds(181));
        var query=new GetStudyMissionUseCase(()->new AuthenticatedUser(mission.userId()),repository,artifacts,
            new JacksonGeneratedActivityContentDecoder(new ObjectMapper()),sources,org.mockito.Mockito.mock(StudentAttemptRepository.class),null,
            java.time.Clock.systemUTC(),receipts,authority);
        var first=query.execute(mission.id(),true,true);var second=query.execute(mission.id(),true,true);
        assertThat(first).isEqualTo(second);assertThat(first.currentActivity()).isNull();assertThat(first.status()).isEqualTo("ACTIVE");
        assertThat(first.initialActivity().state()).isEqualTo("CLAIMED");assertThat(first.initialActivity().canRetry()).isTrue();
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.times(2)).findByMission(mission.userId(),mission.id());
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.times(2)).databaseTime();org.mockito.Mockito.verifyNoMoreInteractions(receipts);
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.times(2)).findOwnedById(mission.id(),mission.userId());
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void refreshRestoresPersistedUngradedReceiptWithoutInventingAResult() {
        var fixture = savedUngradedFixture();
        var receipt = fixture.receipt();
        var originalMission = fixture.mission();
        var query = new GetStudyMissionUseCase(() -> new AuthenticatedUser(OWNER_ID), missions,
                artifacts, new JacksonGeneratedActivityContentDecoder(new ObjectMapper()), sources, fixture.attempts());
        var resumed = query.execute(MISSION_ID);
        assertThat(resumed.latestSubmission()).isEqualTo(receipt);
        assertThat(resumed.latestSubmission().outcome()).isEqualTo("UNGRADED");
        assertThat(resumed.latestSubmission().correctConcepts()).isEmpty();
        assertThat(resumed.latestSubmission().missingConcepts()).isEmpty();
        assertThat(resumed.latestSubmission().misconceptions()).isEmpty();
        assertThat(resumed.latestSubmission().continuationAvailable()).isTrue();
        assertThat(resumed.currentActivity().status()).isEqualTo("COMPLETED");
        assertThat(resumed.currentActivity().content().toString()).doesNotContain("PRIVATE_EXPECTED_ANSWER");
        assertThat(missions.current).isEqualTo(originalMission);
    }

    @Test
    void optInContinuationPreservesTheReceiptAndLegacyReadWhileProjectingTheCurrentDecision() {
        var fixture = savedUngradedFixture();
        var decision = org.mockito.Mockito.mock(StudyMissionContinuationDecision.class);
        var limitation = new com.hippocampus.learning.domain.NextLearningAction(
                LearningActionType.COMMUNICATE_LIMITATION, UUID.randomUUID(), "recall", null,
                "ASSESSMENT_UNAVAILABLE", false);
        org.mockito.Mockito.when(decision.decide(fixture.mission(), fixture.activity(), NOW)).thenReturn(
                new StudyMissionContinuationDecision.Decision(limitation, Map.of()));
        var query = new GetStudyMissionUseCase(() -> new AuthenticatedUser(OWNER_ID), missions,
                artifacts, new JacksonGeneratedActivityContentDecoder(new ObjectMapper()), sources, fixture.attempts(),
                decision, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        var resumed = query.execute(MISSION_ID, true);
        assertThat(resumed.latestSubmission()).isEqualTo(fixture.receipt());
        assertThat(resumed.continuation().actionType()).isEqualTo("COMMUNICATE_LIMITATION");
        assertThat(resumed.continuation().canMaterialize()).isFalse();
        assertThat(query.execute(MISSION_ID).continuation()).isNull();
        assertThat(query.execute(MISSION_ID, true).latestSubmission()).isEqualTo(fixture.receipt());
        assertThat(missions.current).isEqualTo(fixture.mission());
    }

    @Test
    void noProgressPreservesSavedReadButOtherPolicyAndInfrastructureFailuresPropagate() {
        var fixture = savedUngradedFixture();
        var decision = org.mockito.Mockito.mock(StudyMissionContinuationDecision.class);
        var blocked = new com.hippocampus.shared.domain.error.DomainConflictException(
                new com.hippocampus.shared.domain.error.ErrorCode("FOLLOW_UP_NO_PROGRESS"), "Blocked");
        org.mockito.Mockito.when(decision.decide(fixture.mission(), fixture.activity(), NOW)).thenThrow(blocked);
        var query = new GetStudyMissionUseCase(() -> new AuthenticatedUser(OWNER_ID), missions,
                artifacts, new JacksonGeneratedActivityContentDecoder(new ObjectMapper()), sources, fixture.attempts(),
                decision, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        for (int i = 0; i < 2; i++) {
            var result = query.execute(MISSION_ID, true);
            assertThat(result.latestSubmission()).isEqualTo(fixture.receipt());
            assertThat(result.currentActivity().id()).isEqualTo(ACTIVITY_ID);
            assertThat(result.continuation()).isNull();
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(missions.current).isEqualTo(fixture.mission());
        }
        var other = new com.hippocampus.shared.domain.error.DomainConflictException(
                new com.hippocampus.shared.domain.error.ErrorCode("OTHER_CONFLICT"), "Other");
        org.mockito.Mockito.doThrow(other).when(decision).decide(fixture.mission(), fixture.activity(), NOW);
        assertThatThrownBy(() -> query.execute(MISSION_ID, true)).isSameAs(other);
        var unavailable = new IllegalStateException("Database unavailable");
        org.mockito.Mockito.doThrow(unavailable).when(decision).decide(fixture.mission(), fixture.activity(), NOW);
        assertThatThrownBy(() -> query.execute(MISSION_ID, true)).isSameAs(unavailable);
        missions.visible = false;
        assertThatThrownBy(() -> query.execute(MISSION_ID, true)).isInstanceOf(ApplicationNotFoundException.class);
    }

    private SavedUngradedFixture savedUngradedFixture() {
        var active = activity(LearningActivityType.RETRIEVE);
        var completed = new LearningActivity(active.id(), active.learningObjectiveId(),
                active.activityType(), active.representedActionType(), active.questionIntent(),
                active.templateSignature(), "COMPLETED", active.difficulty(), active.sequenceNumber(),
                active.generatedArtifactId(), active.sourceRequired(), active.startedAt(), NOW,
                active.createdAt(), active.sourceReferenceIds());
        missions.current = mission(completed);
        var originalMission = missions.current;
        artifacts.put(artifact("QUESTION", "QUESTION_GENERATION", """
                {"activityType":"SHORT_ANSWER","concept":"radial nerve","learningObjective":"Recall",
                 "question":"Why can radial nerve injury cause wrist drop?","options":[],
                 "correctOption":null,"expectedAnswer":"PRIVATE_EXPECTED_ANSWER",
                 "explanation":"PRIVATE_MECHANISM","difficulty":"FOUNDATIONAL",
                 "sourceReferences":[],"limitations":[]}
                """));
        artifacts.sources = Set.of(SOURCE_ID);
        UUID attemptId = UUID.randomUUID();
        var receipt = new SubmissionReceipt(MISSION_ID, ACTIVITY_ID, attemptId, "UNGRADED",
                List.of(), List.of(), List.of(), "Your response was saved, but could not be graded reliably.",
                "EVALUATION_UNRESOLVED", "ACTIVE", "RETRIEVAL", NOW.toString(), true);
        var attempt = new StudentAttempt(attemptId, OWNER_ID, ACTIVITY_ID, 1,
                "It denervates the wrist extensors.", null, NOW, "UNGRADED", null, null,
                NOW, UUID.randomUUID(), "EVALUATION_UNRESOLVED", receipt.toJson());
        StudentAttemptRepository saved = new StudentAttemptRepository() {
            @Override public StudentAttempt append(StudentAttempt value) { throw new AssertionError("read only"); }
            @Override public List<StudentAttempt> findOwnedByActivity(UUID activityId, UUID ownerId) {
                return ACTIVITY_ID.equals(activityId) && OWNER_ID.equals(ownerId) ? List.of(attempt) : List.of();
            }
        };
        return new SavedUngradedFixture(originalMission, completed, receipt, saved);
    }

    private record SavedUngradedFixture(StudyMission mission, LearningActivity activity,
            SubmissionReceipt receipt, StudentAttemptRepository attempts) {}

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
                 "question":"Explain how an upper-trunk lesion relates to Erb palsy.",
                 "expectedAnswer":"PRIVATE_EXPECTED_ANSWER",
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
        assertThat(content.question()).contains("upper-trunk lesion");
        assertThat(content.toString()).doesNotContain(
                "PRIVATE_EXPECTED_ANSWER", "ignored-provider-reference");
    }

    @Test
    void historicalV1ConnectionRemainsReadableWithoutFabricatingAQuestion() {
        String payload = """
                {"fromConcept":"Preload","toConcept":"Stroke volume",
                 "relationshipType":"DIRECTLY_INFLUENCES",
                 "relationship":"Greater preload can increase stroke volume.",
                 "whyItMatters":"This helps connect venous return with cardiac output.",
                 "sourceReferences":[],"limitations":[]}
                """;
        missions.current = mission(activity(LearningActivityType.CONNECT));
        artifacts.put(artifact(
                "CONCEPT_CONNECTION", "CONCEPT_CONNECTION", payload,
                "CONCEPT_CONNECTION_V1", "1"));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.ConnectionContent content =
                (GetStudyMissionUseCase.ConnectionContent) useCase.execute(MISSION_ID)
                        .currentActivity().content();

        assertThat(content.relationship()).isEqualTo(
                "Greater preload can increase stroke volume.");
        assertThat(content.question()).isNull();
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
    void incompleteConnectionPresentationFailsClosed() {
        String payload = """
                {"fromConcept":"Upper trunk","toConcept":"Erb palsy",
                 "relationshipType":"CLINICAL_CORRELATION",
                 "relationship":"An upper-trunk lesion produces the characteristic deficit.",
                 "whyItMatters":"It localizes the lesion.",
                 "question":"Explain the relationship.",
                 "sourceReferences":[],"limitations":[]}
                """;
        missions.current = mission(activity(LearningActivityType.CONNECT));
        artifacts.put(artifact("CONCEPT_CONNECTION", "CONCEPT_CONNECTION", payload));
        artifacts.sources = Set.of(SOURCE_ID);

        assertNotFound();
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
    void validGeneratedVisualActivityReturnsNullContentWithoutExposingProviderInternals() {
        // Valid VISUAL activity: validated artifact, allowed classification, source IDs match,
        // authorized sources. Expect type=VISUAL, content=null, classification preserved,
        // authorized sources returned. No provider/model/prompt/raw payload exposed.
        missions.current = mission(activity(LearningActivityType.VISUAL));
        artifacts.put(artifact("VISUAL", "VISUAL_GENERATION", "{\"visualPayload\":\"PRIVATE\"}"));
        artifacts.sources = Set.of(SOURCE_ID);

        GetStudyMissionUseCase.CurrentActivity activity = useCase.execute(MISSION_ID).currentActivity();

        assertThat(activity.type()).isEqualTo("VISUAL");
        assertThat(activity.content()).isNull();
        assertThat(activity.classification()).isEqualTo("SOURCE_GROUNDED_GENERATED");
        assertThat(activity.sources()).singleElement().satisfies(source -> {
            assertThat(source.sourceReferenceId()).isEqualTo(SOURCE_ID);
            assertThat(source.materialTitle()).isEqualTo("Upper Limb Lecture");
        });
        // No provider internals exposed via the CurrentActivity record
        assertThat(activity.toString()).doesNotContain(
                "PRIVATE_PROVIDER", "PRIVATE_MODEL", "PRIVATE_MODEL_VERSION",
                "prompt", "visualPayload", "PRIVATE");
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
        String promptId = switch (artifactType) {
            case "CONCEPT_CONNECTION" -> "CONCEPT_CONNECTION_V2";
            case "QUESTION" -> "QUESTION_GENERATION_V1";
            case "EXPLANATION" -> "EXPLANATION_V1";
            case "CONTEXTUAL_APPLICATION" -> "CONTEXTUAL_APPLICATION_V1";
            default -> "prompt";
        };
        String promptVersion = "CONCEPT_CONNECTION_V2".equals(promptId) ? "2" : "1";
        return artifact(artifactType, taskType, payload, promptId, promptVersion);
    }

    private static GeneratedArtifactRepository.GeneratedArtifact artifact(
            String artifactType,
            String taskType,
            String payload,
            String promptId,
            String promptVersion) {
        return new GeneratedArtifactRepository.GeneratedArtifact(
                ARTIFACT_ID, OWNER_ID, artifactType, taskType, "learner content", payload,
                "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", promptId, promptVersion,
                "PRIVATE_PROVIDER", "PRIVATE_MODEL", "PRIVATE_MODEL_VERSION",
                "VALIDATED", true, NOW);
    }

    private static final class InMemoryMissions implements StudyMissionRepository {
        private StudyMission current;
        private boolean visible = true;
        private int saveCalls;

        private InMemoryMissions(StudyMission current) {
            this.current = current;
        }

        @Override public StudyMission save(StudyMission mission) { saveCalls++; current = mission; return mission; }
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
