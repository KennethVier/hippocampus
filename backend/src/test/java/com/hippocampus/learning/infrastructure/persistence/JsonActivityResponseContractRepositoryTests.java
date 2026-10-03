package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.learning.port.GeneratedArtifactRepository;

class JsonActivityResponseContractRepositoryTests {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID ARTIFACT_ID = UUID.randomUUID();

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
