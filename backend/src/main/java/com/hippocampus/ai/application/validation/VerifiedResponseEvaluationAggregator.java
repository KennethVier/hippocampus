package com.hippocampus.ai.application.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.learning.port.ResponseEvaluationPort;

/** Derives a formative outcome only from checked attribution results. */
public final class VerifiedResponseEvaluationAggregator {
    public ResponseEvaluationPort.Result aggregate(
            ResponseEvaluationInput input,
            ResponseAttributionDiscovery discovery,
            ResponseAttributionVerification verification) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(discovery);
        Objects.requireNonNull(verification);
        if (!discovery.complete() || !verification.complete()
                || !discovery.limitations().isEmpty() || !verification.limitations().isEmpty()
                || input.expectedConcepts().isEmpty()
                || verification.assertions().stream().anyMatch(item ->
                        item.disposition() == ResponseAttributionVerification.Disposition.AMBIGUOUS)
                || verification.candidates().stream().anyMatch(item ->
                        item.disposition() == ResponseAttributionVerification.Disposition.AMBIGUOUS)
                || verification.expectedConceptCoverage().stream().anyMatch(item ->
                        item.coverage() == ResponseAttributionVerification.Coverage.AMBIGUOUS)) {
            return uncertain();
        }
        Map<String, ResponseAttributionDiscovery.Candidate> candidates = new LinkedHashMap<>();
        discovery.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        Map<String, String> supported = new LinkedHashMap<>();
        Map<Integer, Boolean> supportedByConcept = new LinkedHashMap<>();
        for (var item : verification.candidates()) {
            if (item.disposition() != ResponseAttributionVerification.Disposition.SUPPORTED) {
                continue;
            }
            var candidate = candidates.get(item.candidateId());
            if (candidate == null || candidate.sourceEvidence().isEmpty()) {
                return uncertain();
            }
            supported.putIfAbsent(key(candidate.constituentMeaning()),
                    candidate.constituentMeaning());
            supportedByConcept.put(candidate.expectedConceptIndex(), true);
        }
        Map<String, String> missing = new LinkedHashMap<>();
        for (var coverage : verification.expectedConceptCoverage()) {
            if (coverage.coverage() == ResponseAttributionVerification.Coverage.COMPLETE) {
                if (!supportedByConcept.containsKey(coverage.expectedConceptIndex())) {
                    return uncertain();
                }
            } else {
                String concept = input.expectedConcepts().get(coverage.expectedConceptIndex());
                missing.putIfAbsent(key(concept), concept);
            }
        }
        Map<String, String> misconceptions = new LinkedHashMap<>();
        verification.misconceptions().forEach(item ->
                misconceptions.putIfAbsent(key(item.description()), item.description()));
        if (verification.mechanismEvidence() == null) {
            return uncertain();
        }
        ResponseEvaluationPort.Outcome outcome;
        if (missing.isEmpty() && misconceptions.isEmpty()) {
            if (supported.isEmpty()
                    || verification.assertions().stream().anyMatch(item ->
                            item.disposition() != ResponseAttributionVerification.Disposition.SUPPORTED)
                    || verification.candidates().stream().anyMatch(item ->
                            item.disposition() != ResponseAttributionVerification.Disposition.SUPPORTED)) {
                return uncertain();
            }
            outcome = ResponseEvaluationPort.Outcome.CORRECT;
        } else if (!supported.isEmpty()) {
            outcome = ResponseEvaluationPort.Outcome.PARTIAL;
        } else {
            outcome = ResponseEvaluationPort.Outcome.INCORRECT;
        }
        List<String> correct = List.copyOf(supported.values());
        List<String> absent = List.copyOf(missing.values());
        List<String> errors = List.copyOf(misconceptions.values());
        StringBuilder feedback = new StringBuilder();
        if (!correct.isEmpty()) {
            feedback.append("You correctly identified ").append(String.join(", ", correct))
                    .append(". ");
        }
        if (!absent.isEmpty()) {
            feedback.append("The missing concept or relationship is ")
                    .append(String.join(", ", absent)).append(". ");
        }
        if (!errors.isEmpty()) {
            feedback.append("Your assertion about ").append(String.join(", ", errors))
                    .append(" is incorrect. ");
        }
        feedback.append("The source explains the mechanism: ")
                .append(verification.mechanismEvidence().sourceText().strip());
        ResponseEvaluationPort.RecommendedAction action = switch (outcome) {
            case CORRECT -> ResponseEvaluationPort.RecommendedAction.CONTINUE;
            case PARTIAL -> ResponseEvaluationPort.RecommendedAction.TARGETED_EXPLANATION;
            case INCORRECT -> ResponseEvaluationPort.RecommendedAction.RETRY;
            case UNCERTAIN -> throw new IllegalStateException("handled by uncertain()");
        };
        return new ResponseEvaluationPort.Result(outcome, correct, absent, errors,
                feedback.toString(), ResponseEvaluationPort.Certainty.SUFFICIENT, action, true);
    }

    public static ResponseEvaluationPort.Result uncertain() {
        return new ResponseEvaluationPort.Result(
                ResponseEvaluationPort.Outcome.UNCERTAIN,
                List.of(), List.of(), List.of(),
                "The response could not be evaluated reliably.",
                ResponseEvaluationPort.Certainty.LIMITED,
                ResponseEvaluationPort.RecommendedAction.RETRY, true);
    }

    private static String key(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
