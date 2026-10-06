package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        assertThat(dataset.version()).isEqualTo("v4");
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
    void retainsV1ResourcesAndCopiesUnaffectedTaskFamiliesToV2() throws IOException {
        Map<String, String> expectedV1Hashes = Map.of(
                "README.md", "6379D66EC33D3F5D7E0336EC182AFE6A0136371DD76A4CB7268259EFF5D15A3D",
                "explanation-cases.json", "2B3274E6DCCFA22132F07CD249E15D75D370AB5E20B9A5B2567D209F514FE42F",
                "question-generation-cases.json", "350AE2555868A5F3EA1151C4031FF5846F2DD9CB86823BE4FDAF56491ECF8142",
                "response-evaluation-cases.json", "0302D8A7ED938A50121E07D6A57E9A2E0954E724687AE7CFDCFF2D4C37098ECD");

        for (Map.Entry<String, String> entry : expectedV1Hashes.entrySet()) {
            assertThat(sha256(resourceBytes("v1", entry.getKey()))).isEqualTo(entry.getValue());
        }
        assertThat(normalizeNewlines(resource("v2", "explanation-cases.json")))
                .isEqualTo(normalizeNewlines(resource("v1", "explanation-cases.json")
                        .replaceFirst("\"version\": \"v1\"", "\"version\": \"v2\"")));
        assertThat(normalizeNewlines(resource("v2", "question-generation-cases.json")))
                .isEqualTo(normalizeNewlines(resource("v1", "question-generation-cases.json")
                        .replaceFirst("\"version\": \"v1\"", "\"version\": \"v2\"")));
    }

    @Test
    void v2ResponseFixturesRequireLearnerDemonstratedKnowledge() {
        GoldenAiDataset.All dataset = loader.loadAll();
        GoldenAiDataset.ResponseEvaluationCase partial = response(dataset, "P7-09-PARTIAL-001");
        GoldenAiDataset.ResponseEvaluationCase wrongReasoning =
                response(dataset, "P7-09-WRONG-REASONING-001");

        assertThat(partial.studentResponse()).isEqualTo("It denervates the wrist extensors.");
        assertThat(partial.allowedEvaluations()).containsExactly(com.hippocampus.ai.domain.Evaluation.PARTIAL);
        assertThat(partial.forbiddenEvaluations())
                .containsExactlyInAnyOrder(
                        com.hippocampus.ai.domain.Evaluation.CORRECT,
                        com.hippocampus.ai.domain.Evaluation.INCORRECT);
        assertThat(partial.requiredCorrectConceptGroups()).noneMatch(group -> group.contains("radial nerve"));
        assertThat(partial.requiredCorrectConceptGroups()).anyMatch(group -> group.stream()
                .anyMatch(term -> term.contains("denervat") || term.contains("loss of input")));
        assertThat(partial.requiredMissingConceptGroups()).anyMatch(group -> group.stream()
                .anyMatch(term -> term.contains("wrist drop") || term.contains("loss of wrist extension")));

        assertThat(wrongReasoning.question()).startsWith("A patient cannot actively extend the wrist");
        assertThat(wrongReasoning.question()).doesNotContain("radial nerve", "wrist drop");
        assertThat(wrongReasoning.allowedEvaluations())
                .containsExactly(com.hippocampus.ai.domain.Evaluation.PARTIAL);
        assertThat(wrongReasoning.requiredCorrectConceptGroups())
                .anyMatch(group -> group.contains("radial nerve"))
                .anyMatch(group -> group.contains("wrist drop"));
        assertThat(wrongReasoning.allowedMisconceptionGroups()).containsExactly(
                List.of("activates the wrist flexors", "normal extensors"));
    }

    @Test
    void v4PreservesConfirmedAlternativesAndAcceptsTheObservedGuidedGapFeedback() {
        GoldenAiDataset.All dataset = loader.loadAll();
        GoldenAiDataset.ResponseEvaluationCase partial =
                response(dataset, "P7-09-PARTIAL-001");

        assertThat(partial.requiredCorrectConceptGroups().getFirst())
                .contains(
                        "radial nerve innervates the wrist extensors injury causes denervation",
                        "radial nerve innervates the wrist extensors",
                        "radial nerve supplies the wrist extensors");
        assertThat(partial.requiredFeedbackConceptGroups().getFirst())
                .contains("what happens to the movement of the wrist when these extensors are denervated");
        assertThat(response(dataset, "P7-09-UNCERTAIN-001").requiredCorrectConceptGroups().getFirst())
                .contains(
                        "a nerve supplies the muscles that extend or lift the wrist",
                        "a nerve supplies the muscles that lift extend the wrist");
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
        assertThatThrownBy(() -> loader.parse(
                        explanations,
                        questions,
                        evaluations.replaceFirst(
                                "\"forbiddenEvaluations\": \\[\"PARTIAL\", \"INCORRECT\", \"UNCERTAIN\"\\]",
                                "\"forbiddenEvaluations\": [\"CORRECT\", \"PARTIAL\", \"INCORRECT\", \"UNCERTAIN\"]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed and forbidden evaluations contradict");
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
        return resource("v2", name);
    }

    private static String resource(String version, String name) throws IOException {
        return new String(resourceBytes(version, name), StandardCharsets.UTF_8);
    }

    private static byte[] resourceBytes(String version, String name) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ai/golden/" + version + "/" + name)) {
            if (input == null) {
                throw new IOException("missing resource " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] normalized = normalizeNewlines(
                    new String(content, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n");
    }

    private static GoldenAiDataset.ResponseEvaluationCase response(
            GoldenAiDataset.All dataset, String caseId) {
        return dataset.responseEvaluations().stream()
                .filter(value -> value.caseId().equals(caseId))
                .findFirst()
                .orElseThrow();
    }
}
