package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.port.ActivityResponseContractRepository.ActivityContractKind;

class JsonActivityResponseContractRepositoryTests {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID ARTIFACT_ID = UUID.randomUUID();

    @Test
    void visualRenderingUsesRepresentedActionAndValidatedPayloadContract() {
        String explanation = """
                {"concept":"Synthetic","explanation":"Synthetic explanation","keyPoints":[],
                 "prerequisitesUsed":[],"sourceReferences":[],"supplementalKnowledgeUsed":false,"limitations":[]}
                """;
        String connection = """
                {"fromConcept":"First","toConcept":"Second","relationshipType":"Synthetic",
                 "relationship":"Synthetic link","whyItMatters":"Synthetic context",
                 "question":"Explain the link.","expectedAnswer":"Synthetic answer",
                 "sourceReferences":[],"limitations":[]}
                """;
        String application = """
                {"scenario":"Synthetic case","targetConcept":"Preload","difficulty":"FOUNDATIONAL_APPLIED",
                 "requiredReasoning":["Mechanism"],"feedbackPoints":["Feedback"],
                 "question":"What happens?","expectedAnswer":"Synthetic answer",
                 "sourceReferences":[],"limitations":[]}
                """;
        String question = """
                {"activityType":"SHORT_ANSWER","concept":"Preload","learningObjective":"Recall preload",
                 "question":"What is preload?","expectedAnswer":"Synthetic answer",
                 "explanation":"Synthetic feedback","difficulty":"FOUNDATIONAL",
                 "options":[],"sourceReferences":[],"limitations":[]}
                """;
        assertVisualContract(LearningActionType.UNDERSTAND, "EXPLANATION", "EXPLANATION",
                "EXPLANATION_V1", "1", explanation, ActivityContractKind.PRESENTATION);
        assertVisualContract(LearningActionType.HINT, "EXPLANATION", "EXPLANATION",
                "EXPLANATION_V1", "1", explanation, ActivityContractKind.PRESENTATION);
        assertVisualContract(LearningActionType.PREREQUISITE_SUPPORT, "EXPLANATION", "EXPLANATION",
                "EXPLANATION_V1", "1", explanation, ActivityContractKind.PRESENTATION);
        assertVisualContract(LearningActionType.CONNECT, "CONCEPT_CONNECTION", "CONCEPT_CONNECTION",
                "CONCEPT_CONNECTION_V2", "2", connection, ActivityContractKind.RESPONSE_BEARING);
        assertVisualContract(LearningActionType.APPLY, "CONTEXTUAL_APPLICATION", "CONTEXTUAL_APPLICATION",
                "CONTEXTUAL_APPLICATION_V1", "1", application, ActivityContractKind.RESPONSE_BEARING);
        assertVisualContract(LearningActionType.RETRIEVE, "QUESTION", "QUESTION_GENERATION",
                "QUESTION_GENERATION_V1", "1", question, ActivityContractKind.RESPONSE_BEARING);
        assertVisualContract(LearningActionType.APPLY, "QUESTION", "QUESTION_GENERATION",
                "QUESTION_GENERATION_V1", "1", question, ActivityContractKind.UNKNOWN);
        assertVisualContract(LearningActionType.UNDERSTAND, "EXPLANATION", "EXPLANATION",
                "EXPLANATION_V1", "1", explanation.replace("\"keyPoints\"", "\"question\":\"Unsafe?\",\"keyPoints\""),
                ActivityContractKind.UNKNOWN);
    }

    private static void assertVisualContract(LearningActionType action, String artifactType, String taskType,
            String prompt, String version, String payload, ActivityContractKind expected) {
        var artifact = new GeneratedArtifactRepository.GeneratedArtifact(ARTIFACT_ID, OWNER_ID,
                artifactType, taskType, "Synthetic", payload, "STRICT_SOURCE",
                "SOURCE_GROUNDED_GENERATED", prompt, version, "provider", "model", "version",
                "VALIDATED", true, Instant.EPOCH);
        var activity = new LearningActivity(ACTIVITY_ID, UUID.randomUUID(), LearningActivityType.VISUAL,
                action, null, null, "PENDING", null, 1, ARTIFACT_ID, false,
                null, null, Instant.EPOCH, Set.of());
        assertThat(repository(artifact).classify(activity, OWNER_ID, "STRICT_SOURCE")).isEqualTo(expected);
    }

    @Test
    void responseBearingConnectionIsDistinctFromPresentationAndUnknown() {
        var repository = repository("""
                {"fromConcept":"First","toConcept":"Second","relationshipType":"Synthetic",
                 "relationship":"Synthetic relationship","whyItMatters":"Synthetic context",
                 "question":"Explain the relationship.","expectedAnswer":"Synthetic answer",
                 "sourceReferences":[],"limitations":[]}
                """);
        var activity = new com.hippocampus.learning.domain.LearningActivity(ACTIVITY_ID, UUID.randomUUID(),
                com.hippocampus.learning.domain.LearningActivityType.CONNECT,
                com.hippocampus.learning.domain.LearningActionType.CONNECT, null, null, "PENDING", null, 1,
                ARTIFACT_ID, false, null, null, Instant.EPOCH, Set.of());
        assertThat(repository.classify(activity, OWNER_ID, "STRICT_SOURCE"))
                .isEqualTo(com.hippocampus.learning.port.ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING);
        assertThat(repository.classify(activity, UUID.randomUUID(), "STRICT_SOURCE"))
                .isEqualTo(com.hippocampus.learning.port.ActivityResponseContractRepository.ActivityContractKind.UNKNOWN);
        assertThat(repository("{}").classify(activity, OWNER_ID, "STRICT_SOURCE"))
                .isEqualTo(com.hippocampus.learning.port.ActivityResponseContractRepository.ActivityContractKind.UNKNOWN);
    }

    @Test
    void validatedConnectionProvidesResponseContractWithDeterministicConceptFallback() {
        var repository = repository("""
                {
                  "fromConcept": "Alveolar ventilation",
                  "toConcept": "Arterial carbon dioxide",
                  "relationshipType": "inverse physiological relationship",
                  "relationship": "Increasing ventilation lowers carbon dioxide.",
                  "whyItMatters": "This explains respiratory compensation.",
                  "question": "Explain how alveolar ventilation affects arterial carbon dioxide.",
                  "expectedAnswer": "Increasing alveolar ventilation lowers arterial carbon dioxide.",
                  "sourceReferences": [],
                  "limitations": []
                }
                """);

        var contract = repository.findValidatedForActivity(
                ACTIVITY_ID, ARTIFACT_ID, OWNER_ID).orElseThrow();

        assertThat(contract.question())
                .isEqualTo("Explain how alveolar ventilation affects arterial carbon dioxide.");
        assertThat(contract.expectedAnswer())
                .isEqualTo("Increasing alveolar ventilation lowers arterial carbon dioxide.");
        assertThat(contract.expectedConcepts())
                .containsExactly("Alveolar ventilation", "Arterial carbon dioxide");
    }

    @Test
    void incompleteConnectionFailsClosed() {
        var repository = repository("""
                {
                  "fromConcept": "Alveolar ventilation",
                  "question": "Explain the relationship.",
                  "expectedAnswer": "Increasing ventilation lowers carbon dioxide."
                }
                """);

        assertThat(repository.findValidatedForActivity(ACTIVITY_ID, ARTIFACT_ID, OWNER_ID))
                .isEmpty();
        assertThat(repository.isHistoricalPresentationOnly(
                ACTIVITY_ID, ARTIFACT_ID, OWNER_ID)).isFalse();
    }

    @Test
    void historicalV1ConnectionDoesNotFabricateResponseEvaluationContract() {
        var repository = repository(artifact("""
                {
                  "fromConcept": "Preload",
                  "toConcept": "Stroke volume",
                  "relationshipType": "DIRECTLY_INFLUENCES",
                  "relationship": "Greater preload can increase stroke volume.",
                  "whyItMatters": "This helps connect venous return with cardiac output.",
                  "sourceReferences": [],
                  "limitations": []
                }
                """, "CONCEPT_CONNECTION_V1", "1"));

        assertThat(repository.findValidatedForActivity(ACTIVITY_ID, ARTIFACT_ID, OWNER_ID))
                .isEmpty();
        assertThat(repository.isHistoricalPresentationOnly(
                ACTIVITY_ID, ARTIFACT_ID, OWNER_ID)).isTrue();
    }

    @Test
    void bootMapperParsingRejectsMalformedPayloadsAndForeignOwnershipWithoutCoercion() {
        for (String payload : java.util.List.of("{", "null", "[]",
                "{\"question\":42,\"expectedAnswer\":\"Expected\"}",
                "{\"question\":\"Question\",\"expectedAnswer\":false}")) {
            assertThat(repository(payload).findValidatedForActivity(ACTIVITY_ID, ARTIFACT_ID, OWNER_ID)).isEmpty();
        }
        var repository = repository("""
                {"fromConcept":"First","toConcept":"Second","question":"Question",
                 "expectedAnswer":"Expected","expectedConcepts":["First",7,"",null,"Second"]}
                """);
        assertThat(repository.findValidatedForActivity(ACTIVITY_ID, ARTIFACT_ID, OWNER_ID).orElseThrow()
                .expectedConcepts()).containsExactly("First", "Second");
        assertThat(repository.findValidatedForActivity(ACTIVITY_ID, ARTIFACT_ID, UUID.randomUUID())).isEmpty();
        assertThat(repository.findValidatedForActivity(ACTIVITY_ID, null, OWNER_ID)).isEmpty();
    }

    @Test void deterministicAdmissionRequiresOwnedMcqShapeAndExactOptions() {
        String payload="""
                {"activityType":"MCQ","concept":"Preload","learningObjective":"Recall preload",
                 "question":"Choose.","expectedAnswer":"B","correctOption":"B",
                 "options":[{"id":"A","text":"First"},{"id":"B","text":"Second"}],
                 "explanation":"Feedback","difficulty":"FOUNDATIONAL","sourceReferences":[],"limitations":[]}
                """;
        var activity=new LearningActivity(ACTIVITY_ID,UUID.randomUUID(),LearningActivityType.RETRIEVE,
                LearningActionType.RETRIEVE,null,null,"PENDING",null,1,ARTIFACT_ID,false,null,null,Instant.EPOCH,Set.of());
        for (String value:java.util.List.of(payload,payload.replace("MCQ","SHORT_ANSWER"),payload.replace("\"correctOption\":\"B\"","\"correctOption\":\"Z\""))) {
            var artifact=new GeneratedArtifactRepository.GeneratedArtifact(ARTIFACT_ID,OWNER_ID,"QUESTION","QUESTION_GENERATION",
                    "fixture",value,"STRICT_SOURCE","SOURCE_GROUNDED_GENERATED","QUESTION_GENERATION_V2","2",
                    "fixture","fixture",null,"VALIDATED",false,Instant.EPOCH);
            var repo=repository(artifact);
            assertThat(repo.findDeterministicForActivity(activity,OWNER_ID,"STRICT_SOURCE").isPresent()).isEqualTo(value.equals(payload));
            assertThat(repo.findDeterministicForActivity(activity,UUID.randomUUID(),"STRICT_SOURCE")).isEmpty();
        }
    }

    private static JsonActivityResponseContractRepository repository(String payload) {
        return repository(artifact(payload));
    }

    private static JsonActivityResponseContractRepository repository(
            GeneratedArtifactRepository.GeneratedArtifact artifact) {
        return new JsonActivityResponseContractRepository(
                new SingleArtifactRepository(artifact), new ObjectMapper());
    }

    private static GeneratedArtifactRepository.GeneratedArtifact artifact(String payload) {
        return artifact(payload, "CONCEPT_CONNECTION_V2", "2");
    }

    private static GeneratedArtifactRepository.GeneratedArtifact artifact(
            String payload, String promptId, String promptVersion) {
        return new GeneratedArtifactRepository.GeneratedArtifact(
                ARTIFACT_ID, OWNER_ID, "CONCEPT_CONNECTION", "CONCEPT_CONNECTION",
                "connection", payload, "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED",
                promptId, promptVersion, "provider", "model", "version",
                "VALIDATED", true, Instant.parse("2026-10-03T00:00:00Z"));
    }

    private record SingleArtifactRepository(GeneratedArtifactRepository.GeneratedArtifact artifact)
            implements GeneratedArtifactRepository {
        @Override public GeneratedArtifactRepository.GeneratedArtifact save(
                GeneratedArtifactRepository.GeneratedArtifact value) { return value; }
        @Override public void addSources(UUID artifactId, Set<UUID> sourceReferenceIds) {}
        @Override
        public Optional<GeneratedArtifactRepository.GeneratedArtifact> findById(UUID artifactId) {
            return artifact.id().equals(artifactId) ? Optional.of(artifact) : Optional.empty();
        }
        @Override public Set<UUID> findSourceReferenceIds(UUID artifactId) { return Set.of(); }
    }
}
