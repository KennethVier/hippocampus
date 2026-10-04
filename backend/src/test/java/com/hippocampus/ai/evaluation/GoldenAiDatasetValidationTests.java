package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenAiDatasetValidationTests {

    private final GoldenAiDatasetLoader loader = new GoldenAiDatasetLoader();

    @Test
    void loadsVersionedDatasetsWithUniqueIdsAndRequiredTaskCoverage() {
        GoldenAiDataset.All dataset = loader.loadAll();
        List<String> caseIds = java.util.stream.Stream.of(
                        dataset.explanations().stream().map(GoldenAiDataset.ExplanationCase::caseId),
                        dataset.questions().stream().map(GoldenAiDataset.QuestionCase::caseId),
                        dataset.responseEvaluations().stream()
                                .map(GoldenAiDataset.ResponseEvaluationCase::caseId))
                .flatMap(stream -> stream)
                .toList();

        assertThat(dataset.version()).isEqualTo("v1");
        assertThat(new HashSet<>(caseIds)).hasSameSizeAs(caseIds);
        assertThat(dataset.explanations()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(dataset.explanations())
                .anyMatch(value -> value.caseId().equals("P7-07-ANAT-001"))
                .anyMatch(GoldenAiDataset.ExplanationCase::requireLimitation);
        assertThat(dataset.questions())
                .extracting(GoldenAiDataset.QuestionCase::activityType)
                .contains(
                        com.hippocampus.ai.domain.ActivityType.SHORT_ANSWER,
                        com.hippocampus.ai.domain.ActivityType.IDENTIFICATION,
                        com.hippocampus.ai.domain.ActivityType.MCQ);
        assertThat(dataset.questions()).anyMatch(value -> !value.recentQuestionIntents().isEmpty());
        assertThat(dataset.responseEvaluations())
                .extracting(GoldenAiDataset.ResponseEvaluationCase::caseId)
                .containsExactlyInAnyOrder(
                        "P7-09-CORRECT-001",
                        "P7-09-PARAPHRASE-001",
                        "P7-09-PARTIAL-001",
                        "P7-09-WRONG-REASONING-001",
                        "P7-09-INCORRECT-001",
                        "P7-09-OFFTOPIC-001",
                        "P7-09-UNCERTAIN-001",
                        "P7-09-EMPTY-001",
                        "P7-09-INJECTION-001");
        assertThat(dataset.responseEvaluations())
                .filteredOn(value -> value.caseId().equals("P7-09-EMPTY-001"))
                .singleElement()
                .extracting(GoldenAiDataset.ResponseEvaluationCase::studentResponse)
                .isEqualTo("");
    }

    @Test
    void rejectsDuplicateCaseIdsAndUnsupportedTasks() throws IOException {
        String explanations = resource("explanation-cases.json");
        String questions = resource("question-generation-cases.json")
                .replace("P7-08-SA-001", "P7-07-ANAT-001");
        String evaluations = resource("response-evaluation-cases.json");

        assertThatThrownBy(() -> loader.parse(explanations, questions, evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate caseId");
        assertThatThrownBy(() -> loader.parse(
                        explanations.replace(
                                "\"task\": \"EXPLANATION\"",
                                "\"task\": \"CONCEPT_CONNECTION\""),
                        resource("question-generation-cases.json"),
                        evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported Golden AI dataset task");
    }

    @Test
    void rejectsMissingOrContradictoryExpectedBehavior() throws IOException {
        String explanations = resource("explanation-cases.json");
        String questions = resource("question-generation-cases.json");
        String evaluations = resource("response-evaluation-cases.json");

        assertThatThrownBy(() -> loader.parse(
                        explanations.replaceFirst(
                                "\\\"requiredConceptGroups\\\"\\s*:\\s*\\[",
                                "\"requiredConceptGroups\": [], \"ignoredGroups\": ["),
                        questions,
                        evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredConceptGroups");
        assertThatThrownBy(() -> loader.parse(
                        explanations.replace(
                                "median nerve causes wrist drop", "radial nerve"),
                        questions,
                        evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradict");
        assertThatThrownBy(() -> loader.parse(
                        explanations,
                        questions,
                        evaluations.replaceFirst(
                                "\\\"allowedEvaluations\\\"\\s*:\\s*\\[\\\"CORRECT\\\"\\]",
                                "\"allowedEvaluations\": []")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedEvaluations");
    }

    @Test
    void rejectsInvalidRequestedActivityTypeOrDifficulty() throws IOException {
        String explanations = resource("explanation-cases.json");
        String questions = resource("question-generation-cases.json");
        String evaluations = resource("response-evaluation-cases.json");

        assertThatThrownBy(() -> loader.parse(
                        explanations,
                        questions.replaceFirst("SHORT_ANSWER", "ESSAY"),
                        evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed Golden AI dataset");
        assertThatThrownBy(() -> loader.parse(
                        explanations,
                        questions.replaceFirst("FOUNDATIONAL", "IMPOSSIBLE"),
                        evaluations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed Golden AI dataset");
    }

    private static String resource(String name) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ai/golden/v1/" + name)) {
            if (input == null) {
                throw new IOException("missing resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
