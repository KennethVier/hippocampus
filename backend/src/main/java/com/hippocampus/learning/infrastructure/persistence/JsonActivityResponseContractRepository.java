package com.hippocampus.learning.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository;

@Repository
@Lazy
public class JsonActivityResponseContractRepository implements ActivityResponseContractRepository {

    private final GeneratedArtifactRepository artifacts;
    private final ObjectMapper objectMapper;

    public JsonActivityResponseContractRepository(
            GeneratedArtifactRepository artifacts,
            ObjectMapper objectMapper) {
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<ResponseContract> findValidatedForActivity(
            UUID activityId, UUID artifactId, UUID ownerId) {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        if (artifactId == null) {
            return Optional.empty();
        }
        return artifacts.findById(artifactId)
                .filter(artifact -> ownerId.equals(artifact.userId()))
                .filter(artifact -> "VALIDATED".equals(artifact.validationStatus()))
                .flatMap(this::parse);
    }

    private Optional<ResponseContract> parse(GeneratedArtifactRepository.GeneratedArtifact artifact) {
        if (artifact.contentPayload() == null || artifact.contentPayload().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(artifact.contentPayload());
            String question = optionalText(root, "question");
            String expectedAnswer = optionalText(root, "expectedAnswer");
            if (question == null || expectedAnswer == null) {
                return Optional.empty();
            }
            List<String> expectedConcepts = textArray(root.path("expectedConcepts"));
            if (expectedConcepts.isEmpty()) {
                String concept = optionalText(root, "concept");
                expectedConcepts = concept == null ? List.of() : List.of(concept);
            }
            return Optional.of(new ResponseContract(
                    question,
                    expectedConcepts,
                    expectedAnswer,
                    optionalText(root, "correctOption"),
                    firstNonBlank(optionalText(root, "explanation"), expectedAnswer)));
        } catch (JsonProcessingException invalidPayload) {
            return Optional.empty();
        }
    }

    private static List<String> textArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.textValue().isBlank()) {
                values.add(value.textValue());
            }
        });
        return List.copyOf(values);
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
