package com.hippocampus.ai.evaluation;

import com.hippocampus.ai.domain.AiTaskType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class GoldenAiDatasetLoader {

    static final String VERSION = "v3";
    private static final String SOURCE_VERSION = "v2";
    private static final String BASE_PATH = "ai/golden/v2/";
    private static final String OVERRIDE_PATH = "ai/golden/v3/response-evaluation-rubric-overrides.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    GoldenAiDataset.All loadAll() {
        GoldenAiDataset.ExplanationFile explanations = readResource(
                "explanation-cases.json", GoldenAiDataset.ExplanationFile.class);
        GoldenAiDataset.QuestionFile questions = readResource(
                "question-generation-cases.json", GoldenAiDataset.QuestionFile.class);
        GoldenAiDataset.ResponseEvaluationFile evaluations = readResource(
                "response-evaluation-cases.json", GoldenAiDataset.ResponseEvaluationFile.class);
        ResponseEvaluationOverrides overrides = readResource(
                OVERRIDE_PATH, ResponseEvaluationOverrides.class, false);
        return validate(explanations, questions, applyOverrides(evaluations, overrides));
    }

    GoldenAiDataset.All parse(String explanations, String questions, String evaluations) {
        try {
            return validate(
                    objectMapper.readValue(explanations, GoldenAiDataset.ExplanationFile.class),
                    objectMapper.readValue(questions, GoldenAiDataset.QuestionFile.class),
                    objectMapper.readValue(evaluations, GoldenAiDataset.ResponseEvaluationFile.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("malformed Golden AI dataset", exception);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private <T> T readResource(String name, Class<T> type) {
        return readResource(name, type, true);
    }

    private <T> T readResource(String name, Class<T> type, boolean relativeToBase) {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(relativeToBase ? BASE_PATH + name : name)) {
            if (input == null) {
                throw new IllegalArgumentException("missing Golden AI dataset resource: " + name);
            }
            return objectMapper.readValue(input, type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot read Golden AI dataset resource: " + name, exception);
        }
    }

    private static GoldenAiDataset.All validate(
            GoldenAiDataset.ExplanationFile explanations,
            GoldenAiDataset.QuestionFile questions,
            GoldenAiDataset.ResponseEvaluationFile evaluations) {
        validateHeader(explanations.version(), explanations.task(), AiTaskType.EXPLANATION);
        validateHeader(questions.version(), questions.task(), AiTaskType.QUESTION_GENERATION);
        validateHeader(evaluations.version(), evaluations.task(), AiTaskType.RESPONSE_EVALUATION);
        requireNonEmpty(explanations.cases(), "explanation cases");
        requireNonEmpty(questions.cases(), "question generation cases");
        requireNonEmpty(evaluations.cases(), "response evaluation cases");

        Set<String> caseIds = new HashSet<>();
        explanations.cases().forEach(value -> {
            validateCommon(value.caseId(), value.subject(), value.topic(), value.learner(),
                    value.sourceEvidence(), caseIds);
            requiredText(value.learningObjective(), "learningObjective");
            requiredText(value.targetConcept(), "targetConcept");
            requireNonNull(value.explanationMode(), "explanationMode");
            requireNonNull(value.groundingMode(), "groundingMode");
            validateConceptExpectations(
                    value.requiredConceptGroups(), value.forbiddenClaims(), value.caseId());
            requireNonNull(value.expectedSupplementalKnowledgeUsed(),
                    "expectedSupplementalKnowledgeUsed");
            if (value.maximumExplanationWords() <= 0) {
                throw invalid(value.caseId(), "maximumExplanationWords must be positive");
            }
        });
        questions.cases().forEach(value -> {
            validateCommon(value.caseId(), value.subject(), value.topic(), value.learner(),
                    value.sourceEvidence(), caseIds);
            requiredText(value.learningObjective(), "learningObjective");
            requiredText(value.targetConcept(), "targetConcept");
            requireNonNull(value.activityType(), "activityType");
            requireNonNull(value.difficulty(), "difficulty");
            requireNonNull(value.groundingMode(), "groundingMode");
            requireNonNull(value.recentQuestionIntents(), "recentQuestionIntents");
            validateConceptExpectations(
                    value.requiredConceptGroups(), value.forbiddenClaims(), value.caseId());
            validateConceptGroups(value.requiredAnswerConceptGroups(),
                    value.caseId(), "requiredAnswerConceptGroups");
            requireNonNull(value.answerLeakageTerms(), "answerLeakageTerms");
        });
        evaluations.cases().forEach(value -> {
            validateCommon(value.caseId(), value.subject(), value.topic(), value.learner(),
                    value.sourceEvidence(), caseIds);
            requiredText(value.question(), "question");
            requireNonEmpty(value.expectedConcepts(), "expectedConcepts");
            requiredText(value.expectedAnswer(), "expectedAnswer");
            requireNonNull(value.studentResponse(), "studentResponse");
            requireNonNull(value.groundingMode(), "groundingMode");
            requireNonEmpty(value.allowedEvaluations(), "allowedEvaluations");
            requireNonNull(value.forbiddenEvaluations(), "forbiddenEvaluations");
            if (value.allowedEvaluations().stream().anyMatch(value.forbiddenEvaluations()::contains)) {
                throw invalid(value.caseId(), "allowed and forbidden evaluations contradict");
            }
            validateOptionalConceptGroups(value.requiredCorrectConceptGroups(),
                    value.caseId(), "requiredCorrectConceptGroups");
            validateOptionalConceptGroups(value.requiredMissingConceptGroups(),
                    value.caseId(), "requiredMissingConceptGroups");
            validateOptionalConceptGroups(value.allowedMisconceptionGroups(),
                    value.caseId(), "allowedMisconceptionGroups");
            validateOptionalConceptGroups(value.requiredFeedbackConceptGroups(),
                    value.caseId(), "requiredFeedbackConceptGroups");
            requireNonNull(value.forbiddenOutputTerms(), "forbiddenOutputTerms");
        });

        return new GoldenAiDataset.All(
                VERSION,
                List.copyOf(explanations.cases()),
                List.copyOf(questions.cases()),
                List.copyOf(evaluations.cases()));
    }

    private static void validateHeader(String version, String task, AiTaskType expectedTask) {
        if (!SOURCE_VERSION.equals(version)) {
            throw new IllegalArgumentException("unsupported Golden AI dataset version: " + version);
        }
        if (!expectedTask.name().equals(task)) {
            throw new IllegalArgumentException("unsupported Golden AI dataset task: " + task);
        }
    }

    private static void validateCommon(
            String caseId,
            String subject,
            String topic,
            GoldenAiDataset.Learner learner,
            List<GoldenAiDataset.Source> sources,
            Set<String> caseIds) {
        requiredText(caseId, "caseId");
        if (!caseIds.add(caseId)) {
            throw invalid(caseId, "duplicate caseId");
        }
        requiredText(subject, "subject");
        requiredText(topic, "topic");
        requireNonNull(learner, "learner");
        requiredText(learner.learningState(), "learner.learningState");
        requiredText(learner.topicExposure(), "learner.topicExposure");
        requiredText(learner.difficultyDirection(), "learner.difficultyDirection");
        requireNonNull(learner.relevantEvidence(), "learner.relevantEvidence");
        requireNonNull(learner.relevantMisconceptions(), "learner.relevantMisconceptions");
        requireNonNull(sources, "sourceEvidence");
        Set<String> sourceIds = new HashSet<>();
        for (GoldenAiDataset.Source source : sources) {
            requireNonNull(source, "sourceEvidence item");
            requiredText(source.sourceId(), "sourceId");
            requiredText(source.content(), "source content");
            try {
                UUID.fromString(source.sourceId());
            } catch (IllegalArgumentException exception) {
                throw invalid(caseId, "sourceId must be a UUID");
            }
            if (!sourceIds.add(source.sourceId())) {
                throw invalid(caseId, "duplicate sourceId");
            }
        }
    }

    private static void validateConceptExpectations(
            List<List<String>> requiredGroups, List<String> forbiddenTerms, String caseId) {
        validateConceptGroups(requiredGroups, caseId, "requiredConceptGroups");
        requireNonNull(forbiddenTerms, "forbidden terms");
        Set<String> required = new HashSet<>();
        requiredGroups.forEach(group -> group.forEach(term -> required.add(normalize(term))));
        if (forbiddenTerms.stream().map(GoldenAiDatasetLoader::normalize).anyMatch(required::contains)) {
            throw invalid(caseId, "required and forbidden expectations contradict");
        }
    }

    private static void validateConceptGroups(
            List<List<String>> groups, String caseId, String name) {
        requireNonEmpty(groups, name);
        validateOptionalConceptGroups(groups, caseId, name);
    }

    private static void validateOptionalConceptGroups(
            List<List<String>> groups, String caseId, String name) {
        requireNonNull(groups, name);
        for (List<String> group : groups) {
            requireNonEmpty(group, name + " group");
            if (group.stream().anyMatch(term -> term == null || term.isBlank())) {
                throw invalid(caseId, name + " contains blank term");
            }
        }
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> void requireNonEmpty(List<T> value, String name) {
        requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static IllegalArgumentException invalid(String caseId, String reason) {
        return new IllegalArgumentException(caseId + ": " + reason);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static GoldenAiDataset.ResponseEvaluationFile applyOverrides(
            GoldenAiDataset.ResponseEvaluationFile evaluations,
            ResponseEvaluationOverrides overrides) {
        if (!VERSION.equals(overrides.version()) || !SOURCE_VERSION.equals(overrides.basedOn())) {
            throw new IllegalArgumentException("unsupported Golden AI rubric override version");
        }
        Map<String, ResponseEvaluationOverride> byCase = new java.util.HashMap<>();
        for (ResponseEvaluationOverride override : overrides.overrides()) {
            requiredText(override.caseId(), "override caseId");
            requireNonEmpty(override.requiredCorrectConceptAlternatives(),
                    "requiredCorrectConceptAlternatives");
            if (override.requiredCorrectConceptAlternatives().stream()
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw invalid(override.caseId(), "override contains blank alternative");
            }
            if (byCase.put(override.caseId(), override) != null) {
                throw invalid(override.caseId(), "duplicate rubric override");
            }
        }
        List<GoldenAiDataset.ResponseEvaluationCase> cases = evaluations.cases().stream()
                .map(value -> applyOverride(value, byCase.remove(value.caseId())))
                .toList();
        if (!byCase.isEmpty()) {
            throw new IllegalArgumentException(
                    "rubric override references unknown case: " + byCase.keySet().iterator().next());
        }
        return new GoldenAiDataset.ResponseEvaluationFile(evaluations.version(), evaluations.task(), cases);
    }

    private static GoldenAiDataset.ResponseEvaluationCase applyOverride(
            GoldenAiDataset.ResponseEvaluationCase value,
            ResponseEvaluationOverride override) {
        if (override == null) {
            return value;
        }
        List<List<String>> correctGroups = new ArrayList<>(value.requiredCorrectConceptGroups());
        if (correctGroups.isEmpty()) {
            throw invalid(value.caseId(), "rubric override requires a correct-concept group");
        }
        List<String> firstGroup = new ArrayList<>(correctGroups.getFirst());
        firstGroup.addAll(override.requiredCorrectConceptAlternatives());
        correctGroups.set(0, List.copyOf(firstGroup));
        return new GoldenAiDataset.ResponseEvaluationCase(
                value.caseId(), value.subject(), value.topic(), value.question(),
                value.expectedConcepts(), value.expectedAnswer(), value.studentResponse(),
                value.groundingMode(), value.learner(), value.sourceEvidence(),
                value.allowedEvaluations(), value.forbiddenEvaluations(), List.copyOf(correctGroups),
                value.requiredMissingConceptGroups(), value.allowedMisconceptionGroups(),
                value.requiredFeedbackConceptGroups(), value.requireNoMissingConcepts(),
                value.requireNoMisconceptions(), value.forbiddenOutputTerms(), value.reviewerNotes());
    }

    private record ResponseEvaluationOverrides(
            String version, String basedOn, List<ResponseEvaluationOverride> overrides) {}

    private record ResponseEvaluationOverride(
            String caseId, List<String> requiredCorrectConceptAlternatives) {}
}
