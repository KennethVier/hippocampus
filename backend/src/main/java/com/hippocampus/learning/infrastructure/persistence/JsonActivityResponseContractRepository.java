package com.hippocampus.learning.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;

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
    public ActivityContractKind classify(LearningActivity activity, UUID ownerId, String groundingMode) {
        if (ActivityResponseContractRepository.isSourceFreeReflection(activity)) return ActivityContractKind.PRESENTATION;
        if (activity.generatedArtifactId() == null) return ActivityContractKind.UNKNOWN;
        var artifact = artifacts.findById(activity.generatedArtifactId())
                .filter(value -> ownerId.equals(value.userId()))
                .filter(value -> activity.generatedArtifactId().equals(value.id()))
                .filter(value -> "VALIDATED".equals(value.validationStatus()))
                .filter(value -> groundingMode.equals(value.groundingMode()))
                .filter(value -> value.classification() != null && java.util.Set.of("SOURCE_DERIVED", "SOURCE_GROUNDED_GENERATED",
                        "SUPPLEMENTAL_GENERATED", "GENERAL_GENERATED").contains(value.classification()))
                .filter(value -> artifacts.findSourceReferenceIds(value.id()).equals(activity.sourceReferenceIds()))
                .filter(value -> !activity.sourceRequired() || !activity.sourceReferenceIds().isEmpty());
        if (artifact.isEmpty()) return ActivityContractKind.UNKNOWN;
        var value = artifact.orElseThrow();
        if (value.contentPayload() == null || value.contentPayload().isBlank()) return ActivityContractKind.UNKNOWN;
        try {
            JsonNode root = objectMapper.readTree(value.contentPayload());
            if (root == null || !root.isObject()) return ActivityContractKind.UNKNOWN;
            boolean explanation = (activity.activityType() == LearningActivityType.UNDERSTAND
                    || activity.activityType() == LearningActivityType.VISUAL)
                    && switch (activity.representedActionType()) {
                        case UNDERSTAND, HINT, PREREQUISITE_SUPPORT -> true;
                        default -> false;
                    }
                    && "EXPLANATION".equals(value.artifactType()) && "EXPLANATION".equals(value.taskType())
                    && ("EXPLANATION_V1".equals(value.promptId()) && "1".equals(value.promptVersion())
                        || "EXPLANATION_V2".equals(value.promptId()) && "2".equals(value.promptVersion()));
            boolean historicalConnection = (activity.activityType() == LearningActivityType.CONNECT
                    || activity.activityType() == LearningActivityType.VISUAL)
                    && activity.representedActionType() == com.hippocampus.learning.domain.LearningActionType.CONNECT
                    && historicalConnection(value);
            // A response-bearing payload can never be admitted as a presentation, even if its
            // metadata incorrectly claims an explanation or historical contract.
            if (explanation || historicalConnection) {
                if (root.has("question") || root.has("expectedAnswer") || root.has("correctOption"))
                    return ActivityContractKind.UNKNOWN;
                return presentationPayload(root, explanation)
                        ? ActivityContractKind.PRESENTATION : ActivityContractKind.UNKNOWN;
            }
            return responseTypeMatches(activity, value) && validResponseShape(root, value)
                    && parse(value).isPresent()
                    ? ActivityContractKind.RESPONSE_BEARING : ActivityContractKind.UNKNOWN;
        } catch (JacksonException | IllegalArgumentException invalidPayload) {
            return ActivityContractKind.UNKNOWN;
        }
    }

    private static boolean responseTypeMatches(LearningActivity activity, GeneratedArtifactRepository.GeneratedArtifact artifact) {
        return switch (activity.activityType()) {
            case RETRIEVE -> (activity.representedActionType()
                    == com.hippocampus.learning.domain.LearningActionType.RETRIEVE
                    || activity.representedActionType()
                    == com.hippocampus.learning.domain.LearningActionType.UNDERSTANDING_CHECK)
                    && "QUESTION".equals(artifact.artifactType())
                    && "QUESTION_GENERATION".equals(artifact.taskType())
                    && ("QUESTION_GENERATION_V1".equals(artifact.promptId()) && "1".equals(artifact.promptVersion())
                        || "QUESTION_GENERATION_V2".equals(artifact.promptId()) && "2".equals(artifact.promptVersion()));
            case CONNECT -> activity.representedActionType()
                    == com.hippocampus.learning.domain.LearningActionType.CONNECT
                    && "CONCEPT_CONNECTION".equals(artifact.artifactType())
                    && "CONCEPT_CONNECTION".equals(artifact.taskType())
                    && "CONCEPT_CONNECTION_V2".equals(artifact.promptId()) && "2".equals(artifact.promptVersion());
            case APPLY -> activity.representedActionType()
                    == com.hippocampus.learning.domain.LearningActionType.APPLY
                    && "CONTEXTUAL_APPLICATION".equals(artifact.artifactType())
                    && "CONTEXTUAL_APPLICATION".equals(artifact.taskType())
                    && "CONTEXTUAL_APPLICATION_V1".equals(artifact.promptId()) && "1".equals(artifact.promptVersion());
            case VISUAL -> switch (activity.representedActionType()) {
                case RETRIEVE -> responseTypeMatches(asType(activity, LearningActivityType.RETRIEVE), artifact);
                case CONNECT -> responseTypeMatches(asType(activity, LearningActivityType.CONNECT), artifact);
                case APPLY -> responseTypeMatches(asType(activity, LearningActivityType.APPLY), artifact);
                default -> false;
            };
            default -> false;
        };
    }

    private static LearningActivity asType(LearningActivity activity, LearningActivityType type) {
        return new LearningActivity(activity.id(), activity.learningObjectiveId(), type,
                activity.representedActionType(), activity.questionIntent(), activity.templateSignature(),
                activity.status(), activity.difficulty(), activity.sequenceNumber(),
                activity.generatedArtifactId(), activity.sourceRequired(), activity.startedAt(),
                activity.completedAt(), activity.createdAt(), activity.sourceReferenceIds());
    }

    private static boolean validResponseShape(JsonNode root, GeneratedArtifactRepository.GeneratedArtifact artifact) {
        JsonNode optionField = root.path("correctOption");
        if (!optionField.isMissingNode() && !optionField.isNull()
                && (!optionField.isTextual() || optionField.textValue().isBlank())) return false;
        String correctOption = optionalText(root, "correctOption");
        if (!validTextArray(root, "sourceReferences", true)
                || !validTextArray(root, "limitations", true)) return false;
        if ("CONCEPT_CONNECTION".equals(artifact.artifactType()))
            return correctOption == null && presentationPayload(root, false);
        String difficulty = optionalText(root, "difficulty");
        if ("CONTEXTUAL_APPLICATION".equals(artifact.artifactType()))
            return correctOption == null && optionalText(root, "scenario") != null
                    && optionalText(root, "targetConcept") != null
                    && difficulty != null && java.util.Set.of("FOUNDATIONAL_APPLIED", "INTERMEDIATE_APPLIED")
                            .contains(difficulty)
                    && validTextArray(root, "requiredReasoning", false)
                    && validTextArray(root, "feedbackPoints", false);
        if (!"QUESTION".equals(artifact.artifactType())
                || optionalText(root, "concept") == null
                || optionalText(root, "learningObjective") == null
                || optionalText(root, "explanation") == null
                || difficulty == null
                || !java.util.Set.of("FOUNDATIONAL", "INTERMEDIATE", "APPLIED")
                        .contains(difficulty)) return false;
        String subtype = optionalText(root, "activityType");
        if (subtype == null) return false;
        if (!"MCQ".equals(subtype))
            return java.util.Set.of("SHORT_ANSWER", "IDENTIFICATION", "EXPLANATION").contains(subtype)
                    && correctOption == null && root.path("options").isArray()
                    && root.path("options").size() == 0;
        if (correctOption == null) return false;
        JsonNode options = root.path("options");
        if (!options.isArray() || options.size() == 0) return false;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonNode option : options) {
            String id = optionalText(option, "id");
            if (id == null || optionalText(option, "text") == null || !ids.add(id)) return false;
        }
        return ids.contains(correctOption);
    }

    private static boolean validTextArray(JsonNode root, String field, boolean allowEmpty) {
        JsonNode values = root.path(field);
        if (!values.isArray() || !allowEmpty && values.size() == 0) return false;
        for (JsonNode value : values) if (!value.isTextual() || value.textValue().isBlank()) return false;
        return true;
    }

    private static boolean presentationPayload(JsonNode root, boolean explanation) {
        List<String> required = explanation ? List.of("concept", "explanation")
                : List.of("fromConcept", "toConcept", "relationshipType", "relationship", "whyItMatters");
        if (required.stream().anyMatch(field -> optionalText(root, field) == null)) return false;
        List<String> arrays = explanation
                ? List.of("keyPoints", "prerequisitesUsed", "sourceReferences", "limitations")
                : List.of("sourceReferences", "limitations");
        for (String field : arrays) {
            JsonNode array = root.path(field);
            if (!array.isArray()) return false;
            for (JsonNode entry : array) if (!entry.isTextual() || entry.textValue().isBlank()) return false;
        }
        return !explanation || root.path("supplementalKnowledgeUsed").isBoolean();
    }

    private static boolean historicalConnection(GeneratedArtifactRepository.GeneratedArtifact artifact) {
        return "CONCEPT_CONNECTION".equals(artifact.artifactType())
                && "CONCEPT_CONNECTION".equals(artifact.taskType())
                && "CONCEPT_CONNECTION_V1".equals(artifact.promptId()) && "1".equals(artifact.promptVersion());
    }

    @Override
    public Optional<DeterministicContract> findDeterministicForActivity(
            LearningActivity activity, UUID ownerId, String groundingMode) {
        if (classify(activity, ownerId, groundingMode) != ActivityContractKind.RESPONSE_BEARING)
            return Optional.empty();
        var artifact = artifacts.findOwnedById(activity.generatedArtifactId(), ownerId);
        if (artifact.isEmpty()) return Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(artifact.orElseThrow().contentPayload());
            if (!"MCQ".equals(optionalText(root, "activityType"))) return Optional.empty();
            var response = findValidatedForActivity(activity.id(), activity.generatedArtifactId(), ownerId);
            if (response.isEmpty() || !response.orElseThrow().supportsDeterministicEvaluation())
                return Optional.empty();
            java.util.Set<String> options = new java.util.HashSet<>();
            for (JsonNode option : root.path("options")) options.add(optionalText(option, "id"));
            return Optional.of(new DeterministicContract(response.orElseThrow(), options));
        } catch (JacksonException | IllegalArgumentException invalidContract) {
            return Optional.empty();
        }
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

    @Override
    public boolean isHistoricalPresentationOnly(
            UUID activityId, UUID artifactId, UUID ownerId) {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        if (artifactId == null) {
            return false;
        }
        return artifacts.findById(artifactId)
                .filter(artifact -> ownerId.equals(artifact.userId()))
                .filter(artifact -> "VALIDATED".equals(artifact.validationStatus()))
                .filter(JsonActivityResponseContractRepository::historicalConnection)
                .filter(artifact -> {
                    try {
                        JsonNode root = objectMapper.readTree(artifact.contentPayload());
                        return root != null && root.isObject() && !root.has("question")
                                && !root.has("expectedAnswer") && presentationPayload(root, false);
                    } catch (JacksonException | IllegalArgumentException invalidPayload) { return false; }
                })
                .isPresent();
    }

    private Optional<ResponseContract> parse(GeneratedArtifactRepository.GeneratedArtifact artifact) {
        if (artifact.contentPayload() == null || artifact.contentPayload().isBlank()) {
            return Optional.empty();
        }
        if ("CONCEPT_CONNECTION".equals(artifact.artifactType())) {
            if (!"CONCEPT_CONNECTION".equals(artifact.taskType())
                    || !"CONCEPT_CONNECTION_V2".equals(artifact.promptId())
                    || !"2".equals(artifact.promptVersion())) {
                return Optional.empty();
            }
        }
        try {
            JsonNode root = objectMapper.readTree(artifact.contentPayload());
            if (root == null || !root.isObject()) return Optional.empty();
            String question = optionalText(root, "question");
            String expectedAnswer = optionalText(root, "expectedAnswer");
            if (question == null || expectedAnswer == null) {
                return Optional.empty();
            }
            List<String> expectedConcepts = textArray(root.path("expectedConcepts"));
            if (expectedConcepts.isEmpty()) {
                String concept = optionalText(root, "concept");
                if (concept != null) {
                    expectedConcepts = List.of(concept);
                } else {
                    expectedConcepts = connectionConcepts(root);
                    if ("CONCEPT_CONNECTION".equals(artifact.artifactType())
                            && expectedConcepts.isEmpty()) {
                        return Optional.empty();
                    }
                }
            }
            return Optional.of(new ResponseContract(
                    question,
                    expectedConcepts,
                    expectedAnswer,
                    optionalText(root, "correctOption"),
                    firstNonBlank(optionalText(root, "explanation"), expectedAnswer)));
        } catch (JacksonException invalidPayload) {
            return Optional.empty();
        }
    }

    private static List<String> connectionConcepts(JsonNode root) {
        String fromConcept = optionalText(root, "fromConcept");
        String toConcept = optionalText(root, "toConcept");
        return fromConcept != null && toConcept != null
                ? List.of(fromConcept, toConcept)
                : List.of();
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
