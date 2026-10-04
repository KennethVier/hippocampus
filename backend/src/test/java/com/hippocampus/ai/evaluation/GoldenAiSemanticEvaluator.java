package com.hippocampus.ai.evaluation;

import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.QuestionOption;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.rag.domain.GroundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class GoldenAiSemanticEvaluator {

    record Result(boolean passed, List<String> failedRules) {
        Result {
            failedRules = List.copyOf(failedRules);
        }
    }

    Result evaluate(GoldenAiDataset.ExplanationCase golden, ExplanationResult actual) {
        List<String> failures = new ArrayList<>();
        String claims = join(actual.concept(), actual.explanation(), actual.keyPoints());
        requireConceptGroups(failures, "required-concept", golden.requiredConceptGroups(), claims);
        rejectTerms(failures, "forbidden-claim", golden.forbiddenClaims(), claims);
        requireSourceSubset(failures, golden.sourceEvidence(), actual.sourceReferences());
        if (!golden.sourceEvidence().isEmpty()
                && golden.groundingMode() != GroundingMode.GENERAL_KNOWLEDGE
                && actual.sourceReferences().isEmpty()) {
            failures.add("source-grounded-output-requires-reference");
        }
        if (golden.requireLimitation() && actual.limitations().isEmpty()) {
            failures.add("required-limitation-missing");
        }
        if (!golden.requireLimitation() && !actual.limitations().isEmpty()) {
            failures.add("unexpected-limitation");
        }
        if (actual.supplementalKnowledgeUsed()
                != golden.expectedSupplementalKnowledgeUsed().booleanValue()) {
            failures.add("supplemental-knowledge-flag-mismatch");
        }
        if (actual.keyPoints().isEmpty() || actual.keyPoints().stream().anyMatch(String::isBlank)) {
            failures.add("key-points-must-be-non-empty");
        }
        if (wordCount(actual.explanation()) > golden.maximumExplanationWords()) {
            failures.add("explanation-exceeds-case-word-limit");
        }
        if (!golden.requireLimitation()
                && golden.groundingMode() != GroundingMode.GENERAL_KNOWLEDGE
                && !actual.supplementalKnowledgeUsed()) {
            String sourceText = golden.sourceEvidence().stream()
                    .map(GoldenAiDataset.Source::content)
                    .collect(Collectors.joining(" "));
            requireConceptGroups(
                    failures, "required-concept-not-supported-by-source",
                    golden.requiredConceptGroups(), sourceText);
        }
        return result(failures);
    }

    Result evaluate(GoldenAiDataset.QuestionCase golden, QuestionGenerationResult actual) {
        List<String> failures = new ArrayList<>();
        if (actual.activityType() != golden.activityType()) {
            failures.add("requested-activity-type-not-preserved");
        }
        if (actual.difficulty() != golden.difficulty()) {
            failures.add("requested-difficulty-not-preserved");
        }
        if (!normalize(actual.learningObjective()).equals(normalize(golden.learningObjective()))) {
            failures.add("learning-objective-not-preserved");
        }
        if (!aligned(actual.concept(), golden.targetConcept())) {
            failures.add("target-concept-misaligned");
        }
        String allContent = join(
                actual.concept(), actual.question(), actual.expectedAnswer(), actual.explanation(),
                actual.options().stream().map(QuestionOption::text).toList());
        requireConceptGroups(failures, "required-concept", golden.requiredConceptGroups(), allContent);
        requireConceptGroups(
                failures, "expected-answer-concept", golden.requiredAnswerConceptGroups(),
                actual.expectedAnswer());
        String sourceText = golden.sourceEvidence().stream()
                .map(GoldenAiDataset.Source::content)
                .collect(Collectors.joining(" "));
        requireConceptGroups(
                failures, "expected-answer-unsupported-by-source",
                golden.requiredAnswerConceptGroups(), sourceText);
        rejectTerms(failures, "answer-leakage", golden.answerLeakageTerms(), actual.question());
        rejectTerms(failures, "forbidden-claim", golden.forbiddenClaims(), allContent);
        requireSourceSubset(failures, golden.sourceEvidence(), actual.sourceReferences());
        if (!golden.sourceEvidence().isEmpty()
                && golden.groundingMode() != GroundingMode.GENERAL_KNOWLEDGE
                && actual.sourceReferences().isEmpty()) {
            failures.add("source-grounded-output-requires-reference");
        }
        for (String recentIntent : golden.recentQuestionIntents()) {
            if (nearDuplicate(actual.question(), recentIntent)) {
                failures.add("duplicates-recent-question-intent");
                break;
            }
        }
        return result(failures);
    }

    Result evaluate(
            GoldenAiDataset.ResponseEvaluationCase golden,
            ResponseEvaluationResult actual) {
        List<String> failures = new ArrayList<>();
        if (!golden.allowedEvaluations().contains(actual.evaluation())) {
            failures.add("evaluation-not-allowed");
        }
        if (golden.forbiddenEvaluations().contains(actual.evaluation())) {
            failures.add("forbidden-evaluation");
        }
        requireConceptGroups(
                failures, "required-correct-concept",
                golden.requiredCorrectConceptGroups(), join(actual.correctConcepts()));
        requireConceptGroups(
                failures, "required-missing-concept",
                golden.requiredMissingConceptGroups(), join(actual.missingConcepts()));
        requireConceptGroups(
                failures, "feedback-does-not-identify-gap",
                golden.requiredFeedbackConceptGroups(), actual.feedback());
        if (golden.requireNoMissingConcepts() && !actual.missingConcepts().isEmpty()) {
            failures.add("unexpected-missing-concept");
        }
        if (golden.requireNoMisconceptions() && !actual.misconceptions().isEmpty()) {
            failures.add("fabricated-misconception");
        }
        if (!actual.misconceptions().isEmpty()) {
            for (String misconception : actual.misconceptions()) {
                if (!matchesAnyGroup(misconception, golden.allowedMisconceptionGroups())) {
                    failures.add("misconception-not-demonstrated-by-response");
                    break;
                }
            }
        }
        String allOutput = join(
                actual.correctConcepts(), actual.missingConcepts(), actual.misconceptions(),
                actual.feedback(), actual.limitations());
        rejectTerms(failures, "forbidden-output-term", golden.forbiddenOutputTerms(), allOutput);
        rejectTerms(
                failures,
                "learning-state-decision-in-output",
                List.of("mastery", "mastered", "advance to the next", "progress to the next"),
                allOutput);
        requireSourceSubset(failures, golden.sourceEvidence(), actual.sourceReferences());
        return result(failures);
    }

    private static void requireSourceSubset(
            List<String> failures,
            List<GoldenAiDataset.Source> sources,
            List<String> actualReferences) {
        Set<String> allowed = sources.stream()
                .map(GoldenAiDataset.Source::sourceId)
                .collect(Collectors.toSet());
        if (actualReferences.stream().anyMatch(reference -> !allowed.contains(reference))) {
            failures.add("source-reference-outside-supplied-evidence");
        }
    }

    private static void requireConceptGroups(
            List<String> failures, String rule, List<List<String>> groups, String actualText) {
        for (List<String> group : groups) {
            if (!containsAny(actualText, group)) {
                failures.add(rule + ":" + String.join("|", group));
            }
        }
    }

    private static void rejectTerms(
            List<String> failures, String rule, List<String> forbiddenTerms, String actualText) {
        String normalized = normalize(actualText);
        for (String term : forbiddenTerms) {
            if (normalized.contains(normalize(term))) {
                failures.add(rule + ":" + term);
            }
        }
    }

    private static boolean matchesAnyGroup(String value, List<List<String>> groups) {
        return groups.stream().anyMatch(group -> containsAny(value, group));
    }

    private static boolean containsAny(String value, List<String> alternatives) {
        String normalized = normalize(value);
        return alternatives.stream().map(GoldenAiSemanticEvaluator::normalize)
                .anyMatch(normalized::contains);
    }

    private static boolean aligned(String actual, String expected) {
        String normalizedActual = normalize(actual);
        String normalizedExpected = normalize(expected);
        return normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual);
    }

    private static boolean nearDuplicate(String first, String second) {
        String normalizedFirst = normalize(first);
        String normalizedSecond = normalize(second);
        if (normalizedFirst.contains(normalizedSecond) || normalizedSecond.contains(normalizedFirst)) {
            return true;
        }
        Set<String> firstTokens = tokens(normalizedFirst);
        Set<String> secondTokens = tokens(normalizedSecond);
        Set<String> intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        Set<String> union = new HashSet<>(firstTokens);
        union.addAll(secondTokens);
        return !union.isEmpty() && (double) intersection.size() / union.size() >= 0.75d;
    }

    private static Set<String> tokens(String value) {
        return Arrays.stream(value.split(" "))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toSet());
    }

    private static int wordCount(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? 0 : normalized.split(" ").length;
    }

    private static String join(Object... values) {
        return Arrays.stream(values)
                .flatMap(value -> value instanceof List<?> list
                        ? list.stream().map(String::valueOf)
                        : java.util.stream.Stream.of(String.valueOf(value)))
                .collect(Collectors.joining(" "));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Result result(List<String> failures) {
        return new Result(failures.isEmpty(), failures);
    }
}
