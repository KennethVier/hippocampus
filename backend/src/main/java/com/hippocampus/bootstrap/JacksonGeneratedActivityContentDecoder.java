package com.hippocampus.bootstrap;

import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.port.GeneratedActivityContentDecoder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Lazy
public final class JacksonGeneratedActivityContentDecoder
        implements GeneratedActivityContentDecoder {

    private final ObjectMapper objectMapper;

    public JacksonGeneratedActivityContentDecoder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public DecodedContent decode(
            LearningActivityType activityType,
            String artifactType,
            String taskType,
            String contentPayload) {
        Objects.requireNonNull(activityType, "activityType must not be null");
        if (contentPayload == null || contentPayload.isBlank()) {
            throw new IllegalArgumentException("validated generated activity payload is missing");
        }
        try {
            return switch (activityType) {
                case UNDERSTAND -> {
                    requireContract(artifactType, taskType, "EXPLANATION", "EXPLANATION");
                    ExplanationResult value = objectMapper.readValue(
                            contentPayload, ExplanationResult.class);
                    yield new Explanation(
                            value.concept(), value.explanation(), value.keyPoints(), value.limitations());
                }
                case RETRIEVE -> {
                    requireContract(artifactType, taskType, "QUESTION", "QUESTION_GENERATION");
                    QuestionGenerationResult value = objectMapper.readValue(
                            contentPayload, QuestionGenerationResult.class);
                    yield new Retrieval(
                            value.activityType().name(), value.concept(), value.question(),
                            value.options().stream().map(option -> new Option(option.id(), option.text())).toList(),
                            value.difficulty().name(), value.limitations());
                }
                case CONNECT -> {
                    requireContract(artifactType, taskType, "CONCEPT_CONNECTION", "CONCEPT_CONNECTION");
                    ConceptConnectionResult value = objectMapper.readValue(
                            contentPayload, ConceptConnectionResult.class);
                    yield new Connection(
                            value.fromConcept(), value.toConcept(), value.relationshipType(),
                            value.relationship(), value.whyItMatters(), value.question(),
                            value.limitations());
                }
                case APPLY -> {
                    requireContract(
                            artifactType, taskType, "CONTEXTUAL_APPLICATION", "CONTEXTUAL_APPLICATION");
                    ContextualApplicationResult value = objectMapper.readValue(
                            contentPayload, ContextualApplicationResult.class);
                    yield new Application(
                            value.scenario(), value.question(), value.targetConcept(),
                            value.difficulty().name(), value.limitations());
                }
                case VISUAL, FEEDBACK, REFLECT -> throw new IllegalArgumentException(
                        "generated payload is not supported for " + activityType);
            };
        } catch (JacksonException invalidPayload) {
            throw new IllegalArgumentException("validated generated activity payload is malformed", invalidPayload);
        }
    }

    private static void requireContract(
            String artifactType,
            String taskType,
            String expectedArtifactType,
            String expectedTaskType) {
        if (!expectedArtifactType.equals(artifactType) || !expectedTaskType.equals(taskType)) {
            throw new IllegalArgumentException("generated artifact contract does not match activity type");
        }
    }
}
