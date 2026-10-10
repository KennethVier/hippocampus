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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GoldenAiSemanticEvaluator {

    private static final Set<String> HARMLESS_ARTICLES = Set.of("a", "an", "the");
    private static final Set<String> NEGATIONS = Set.of("no", "not", "never", "without");
    private static final int NEGATION_LOOKBACK_TOKENS = 3;
    private static final String INJURY = "(?:radial nerve injury|loss of radial nerve function)";
    private static final String CAUSES = "(?:causes|leads to|results in|produces)";
    private static final String EXTENSOR_LOSS = "(?:loss of wrist extension|denervation of wrist extensors"
            + "|loss of wrist extensor function|loss of input to wrist extensors|loss of innervation of wrist extensors)";
    private static final Pattern REVERSED_OR_PRESERVED_MECHANISM = Pattern.compile(
            "\\b(?:" + EXTENSOR_LOSS + " " + CAUSES + " " + INJURY
                    + "|" + INJURY + " (?:preserves|maintains) (?:wrist extensor (?:input|function|innervation)"
                    + "|input to wrist extensors|innervation of wrist extensors|wrist extension|their input|their function)"
                    + "|" + INJURY + " leaves wrist extensors (?:normal|functional|intact)"
                    + "|wrist extensors (?:remain normal|retain (?:input|function|innervation)) (?:after|during) "
                    + INJURY + ")\\b");
    private static final String WRIST_POSITION_QUESTION =
            "(?:to (?:complete your answer|fully answer question) )?what happens to wrist(?: position| posture)?";
    private static final Pattern WRIST_POSITION_CONSEQUENCE_QUESTION = Pattern.compile(
            WRIST_POSITION_QUESTION + " (?:as result of|as consequence of|because of) "
                    + "(?:losing(?: wrist)? extension|loss of(?: wrist)? extension)");
    private static final Pattern PRESERVED_EXTENSION_FEEDBACK = Pattern.compile(
            "\\b(?:wrist extension (?:is |remains )?(?:preserved|normal|intact)"
                    + "|wrist extensors (?:remain|are) (?:functional|normal|intact)"
                    + "|normal wrist extension (?:after|following|despite) denervation"
                    + "|denervation (?:preserves|maintains) wrist extension)\\b");

    enum SemanticMatchMode {
        ASSERTION,
        GAP_MENTION
    }

    record Result(boolean passed, List<String> failedRules) {
        Result {
            failedRules = List.copyOf(failedRules);
        }
    }

    Result evaluate(GoldenAiDataset.ExplanationCase golden, ExplanationResult actual) {
        List<String> failures = new ArrayList<>();
        String claims = join(actual.concept(), actual.explanation(), actual.keyPoints());
        requireConceptGroups(
                failures, "required-concept", golden.requiredConceptGroups(), claims,
                SemanticMatchMode.ASSERTION);
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
        if (actual.supplementalKnowledgeUsed()
                != golden.expectedSupplementalKnowledgeUsed().booleanValue()) {
            failures.add("supplemental-knowledge-flag-mismatch");
        }
        if ((!golden.requireLimitation() && actual.keyPoints().isEmpty())
                || actual.keyPoints().stream().anyMatch(String::isBlank)) {
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
                    golden.requiredConceptGroups(), sourceText, SemanticMatchMode.ASSERTION);
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
        requireConceptGroups(
                failures, "required-concept", golden.requiredConceptGroups(), allContent,
                SemanticMatchMode.ASSERTION);
        requireConceptGroups(
                failures, "expected-answer-concept", golden.requiredAnswerConceptGroups(),
                actual.expectedAnswer(), SemanticMatchMode.ASSERTION);
        String sourceText = golden.sourceEvidence().stream()
                .map(GoldenAiDataset.Source::content)
                .collect(Collectors.joining(" "));
        requireConceptGroups(
                failures, "expected-answer-unsupported-by-source",
                golden.requiredAnswerConceptGroups(), sourceText, SemanticMatchMode.ASSERTION);
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
                golden.requiredCorrectConceptGroups(), join(actual.correctConcepts()),
                SemanticMatchMode.ASSERTION);
        if (golden.caseId().equals("P7-09-INCORRECT-001")
                && golden.allowedEvaluations().equals(List.of(com.hippocampus.ai.domain.Evaluation.PARTIAL))) {
            List<String> paralysis = golden.requiredCorrectConceptGroups().getFirst();
            if (!matchesSemanticGroup(golden.studentResponse(), paralysis, SemanticMatchMode.ASSERTION)
                    || actual.correctConcepts().stream().noneMatch(concept ->
                            matchesSemanticGroup(concept, paralysis, SemanticMatchMode.ASSERTION)
                                    && !concept.toLowerCase(java.util.Locale.ROOT).contains("median nerve")
                                    && !concept.toLowerCase(java.util.Locale.ROOT).contains("radial nerve"))) {
                failures.add("correct-concept-not-independently-demonstrated");
            }
            if (actual.misconceptions().stream().noneMatch(misconception ->
                    matchesAnyGroup(misconception, golden.allowedMisconceptionGroups(), SemanticMatchMode.ASSERTION))) {
                failures.add("wrong-nerve-misconception-required");
            }
        }
        for (List<String> group : golden.requiredMissingConceptGroups()) {
            boolean functionalMechanism = group.contains("radial nerve injury eliminates wrist extension");
            boolean mechanismGroup = golden.caseId().equals("P7-09-WRONG-REASONING-001")
                    && (group.contains("denervation of wrist extensors") || functionalMechanism);
            boolean contradictedMechanism = mechanismGroup
                    && hasBoundedAssertion(pedagogicalClaims(actual), REVERSED_OR_PRESERVED_MECHANISM);
            boolean namedGap = functionalMechanism
                    ? matchesFunctionalMechanism(actual.missingConcepts())
                    : mechanismGroup
                    ? matchesMissingDenervation(actual.missingConcepts(), group)
                    : actual.missingConcepts().stream().anyMatch(concept ->
                            matchesSemanticGroup(concept, group, SemanticMatchMode.GAP_MENTION));
            if (contradictedMechanism || !namedGap) {
                failures.add("required-missing-concept:" + String.join("|", group));
            }
        }
        for (List<String> group : golden.requiredFeedbackConceptGroups()) {
            boolean matches;
            if (golden.caseId().equals("P7-09-PARTIAL-001")) {
                matches = matchesPartialFeedbackGap(actual.feedback(), group);
            } else if (group.contains("radial nerve injury eliminates wrist extension")) {
                matches = matchesFunctionalMechanism(List.of(actual.feedback()))
                        && !hasBoundedAssertion(List.of(actual.feedback()), REVERSED_OR_PRESERVED_MECHANISM)
                        && !hasBoundedAssertion(List.of(actual.feedback()), PRESERVED_EXTENSION_FEEDBACK);
            } else if (golden.caseId().equals("P7-09-WRONG-REASONING-001")
                    && group.contains("loss of wrist extension")) {
                matches = !hasBoundedAssertion(List.of(actual.feedback()), REVERSED_OR_PRESERVED_MECHANISM)
                        && !hasBoundedAssertion(List.of(actual.feedback()), PRESERVED_EXTENSION_FEEDBACK)
                        && (WristDropRelations.functionalLoss(actual.feedback(), true)
                                || (group.contains("denervation of wrist extensors")
                                        && hasAssertion(List.of(actual.feedback()), group.stream()
                                                .filter(alternative -> !alternative.equals("loss of wrist extension"))
                                                .toList())));
            } else {
                matches = matchesSemanticGroup(actual.feedback(), group, SemanticMatchMode.GAP_MENTION);
            }
            if (!matches) {
                failures.add("feedback-does-not-identify-gap:" + String.join("|", group));
            }
        }
        if (golden.requireNoMissingConcepts() && !actual.missingConcepts().isEmpty()) {
            failures.add("unexpected-missing-concept");
        }
        if (golden.requireNoMisconceptions() && !actual.misconceptions().isEmpty()) {
            failures.add("fabricated-misconception");
        }
        if (!actual.misconceptions().isEmpty()) {
            for (String misconception : actual.misconceptions()) {
                if (!matchesDemonstratedMisconception(golden, misconception)) {
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

    private static boolean matchesDemonstratedMisconception(
            GoldenAiDataset.ResponseEvaluationCase golden, String misconception) {
        if (golden.caseId().equals("P7-09-WRONG-REASONING-001")) {
            return golden.allowedMisconceptionGroups().stream().anyMatch(group -> group.contains("normal extensors"))
                    && WristDropRelations.grounded(golden.studentResponse(), misconception);
        }
        return matchesAnyGroup(misconception, golden.allowedMisconceptionGroups(), SemanticMatchMode.ASSERTION);
    }

    private static boolean matchesPartialFeedbackGap(String feedback, List<String> alternatives) {
        if (hasBoundedAssertion(List.of(feedback), REVERSED_OR_PRESERVED_MECHANISM)
                || hasBoundedAssertion(List.of(feedback), PRESERVED_EXTENSION_FEEDBACK)) {
            return false;
        }
        boolean identifiesGap = false;
        for (String sentence : feedback.split("[.!?;]")) {
            // Correct only the observed joined preposition locally; do not broaden normalization
            // for other fields/tasks. The complete question must link position to lost extension.
            String normalized = String.join(" ", semanticTokens(
                    sentence.toLowerCase(Locale.ROOT).replaceAll("\\btothe\\b", "to the")));
            if (normalized.matches(WRIST_POSITION_QUESTION + "(?: .*)?")) {
                // A familiar gap phrase cannot rescue a position question with an invented
                // cause or an incomplete relationship elsewhere in that same question.
                if (!WRIST_POSITION_CONSEQUENCE_QUESTION.matcher(normalized).matches()) {
                    return false;
                }
                identifiesGap = true;
            } else if (matchesSemanticGroup(sentence, alternatives, SemanticMatchMode.GAP_MENTION)) {
                identifiesGap = true;
            }
        }
        return identifiesGap;
    }

    private static boolean matchesFunctionalMechanism(List<String> concepts) {
        return concepts.stream().anyMatch(concept -> WristDropRelations.functionalLoss(concept, false));
    }

    private static boolean matchesMissingDenervation(List<String> missingConcepts, List<String> alternatives) {
        for (String concept : missingConcepts) {
            if (semanticTokens(concept).contains("denervates")) {
                // A stated denervation action needs its bounded causal subject. Nominal gap
                // descriptions retain the existing rubric; other subjects cannot borrow its verb.
                if (WristDropRelations.functionalLoss(concept, false)) {
                    return true;
                }
            } else if (matchesSemanticGroup(concept, alternatives, SemanticMatchMode.GAP_MENTION)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> pedagogicalClaims(ResponseEvaluationResult actual) {
        List<String> claims = new ArrayList<>(actual.correctConcepts());
        claims.addAll(actual.missingConcepts());
        claims.add(actual.feedback());
        return claims;
    }

    private static boolean hasBoundedAssertion(List<String> claims, Pattern relationship) {
        for (String claim : claims) {
            // A subject in one sentence must not become the cause of a separate sentence.
            for (String sentence : claim.split("[.!?;]")) {
                String normalized = String.join(" ", semanticTokens(sentence));
                var matcher = relationship.matcher(normalized);
                while (matcher.find()) {
                    if (matchesSemanticGroup(sentence, List.of(matcher.group()), SemanticMatchMode.ASSERTION)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAssertion(List<String> claims, List<String> alternatives) {
        return claims.stream().anyMatch(claim -> matchesSemanticGroup(claim, alternatives, SemanticMatchMode.ASSERTION));
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
            List<String> failures,
            String rule,
            List<List<String>> groups,
            String actualText,
            SemanticMatchMode mode) {
        for (List<String> group : groups) {
            if (!matchesSemanticGroup(actualText, group, mode)) {
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

    private static boolean matchesAnyGroup(
            String value, List<List<String>> groups, SemanticMatchMode mode) {
        return groups.stream().anyMatch(group -> matchesSemanticGroup(value, group, mode));
    }

    static boolean matchesSemanticGroup(String value, List<String> alternatives) {
        return matchesSemanticGroup(value, alternatives, SemanticMatchMode.ASSERTION);
    }

    static boolean matchesSemanticGroup(
            String value, List<String> alternatives, SemanticMatchMode mode) {
        List<String> valueTokens = semanticTokens(value);
        return alternatives.stream().anyMatch(alternative -> WristDropRelations.equivalent(value, alternative))
                || alternatives.stream()
                .map(GoldenAiSemanticEvaluator::semanticTokens)
                .anyMatch(alternative -> containsSemanticPhrase(valueTokens, alternative, mode));
    }

    private static boolean containsSemanticPhrase(
            List<String> valueTokens,
            List<String> alternativeTokens,
            SemanticMatchMode mode) {
        if (alternativeTokens.isEmpty() || alternativeTokens.size() > valueTokens.size()) {
            return false;
        }
        boolean positiveAlternative = alternativeTokens.stream().noneMatch(NEGATIONS::contains);
        int lastStart = valueTokens.size() - alternativeTokens.size();
        for (int start = 0; start <= lastStart; start++) {
            if (valueTokens.subList(start, start + alternativeTokens.size()).equals(alternativeTokens)
                    && (mode == SemanticMatchMode.GAP_MENTION
                            || !positiveAlternative
                            || !hasPrecedingNegation(valueTokens, start))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPrecedingNegation(List<String> tokens, int phraseStart) {
        int firstChecked = Math.max(0, phraseStart - NEGATION_LOOKBACK_TOKENS);
        return tokens.subList(firstChecked, phraseStart).stream().anyMatch(NEGATIONS::contains);
    }

    private static List<String> semanticTokens(String value) {
        // Canonicalize only bounded wrist-movement constructions; retain token order and negations.
        String normalized = normalize(value)
                .replaceAll("\\bmuscles that (?:extend (?:or )?lift|extend|lift) (?:the )?wrist\\b",
                        "wrist extensors")
                .replaceAll("\\bhand dropping\\b", "wrist drop")
                .replaceAll("\\beliminating wrist extension\\b", "loss of wrist extension");
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> !HARMLESS_ARTICLES.contains(token))
                .toList();
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
