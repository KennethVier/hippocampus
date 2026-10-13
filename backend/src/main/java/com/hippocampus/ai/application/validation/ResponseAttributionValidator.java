package com.hippocampus.ai.application.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;

/** Checks model claims against the immutable response and owner-scoped source package. */
public final class ResponseAttributionValidator {
    private static final Set<String> INCIDENTAL_WORDS = Set.of(
            "a", "an", "the", "i", "it", "is", "are", "was", "were", "be", "been",
            "being", "to", "of", "in", "on", "at", "for", "with", "by", "from",
            "as", "this", "that", "these", "those", "my", "your", "their", "its",
            "and", "but", "or", "however", "although", "though", "yet", "whereas",
            "because", "since", "so", "therefore", "thus", "while");
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:and|but|or|however|although|though|yet|whereas|because|since|so|therefore|thus|while)(?![\\p{L}\\p{N}])");
    private final Limits limits;

    public ResponseAttributionValidator(Limits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    public void validateDiscovery(ResponseEvaluationInput input,
            AuthorizedResponseEvidence evidence, ResponseAttributionDiscovery discovery) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(discovery);
        if (input.studentResponse().length() > limits.maxResponseCharacters()
                || discovery.assertions().size() > limits.maxAssertions()
                || discovery.candidates().size() > limits.maxCandidates()
                || evidence.sources().size() > limits.maxSources()
                || evidence.sources().stream().mapToInt(source -> source.content().length()).sum()
                        > limits.maxSourceCharacters()
                || discovery.candidates().stream().mapToInt(candidate ->
                        candidate.sourceEvidence().size()).sum() > limits.maxSourceSpans()) {
            throw invalid("response attribution limit exceeded");
        }
        requireCompleteIndexes(discovery.coveredExpectedConceptIndexes(),
                input.expectedConcepts().size());
        Map<String, ResponseAttributionDiscovery.Assertion> assertions = new HashMap<>();
        for (var assertion : discovery.assertions()) {
            requireText(assertion.id());
            requireText(assertion.proposition());
            if (!Set.of("AFFIRMED", "NEGATED", "UNCERTAIN").contains(assertion.polarity())
                    || assertions.putIfAbsent(assertion.id(), assertion) != null) {
                throw invalid("invalid or duplicate assertion");
            }
            requireSpan(input.studentResponse(), assertion.studentStart(),
                    assertion.studentEnd(), assertion.studentText());
            if (assertion.relatedExpectedConceptIndexes().isEmpty()) {
                throw invalid("assertion has no concept relation");
            }
            requireDistinctValidIndexes(assertion.relatedExpectedConceptIndexes(),
                    input.expectedConcepts().size());
            assertion.qualifiers().forEach(ResponseAttributionValidator::requireText);
        }
        requireStudentContentCoverage(input.studentResponse(), discovery.assertions());
        Map<UUID, AuthorizedResponseEvidence.Source> sources = new HashMap<>();
        for (var source : evidence.sources()) {
            if (sources.putIfAbsent(source.sourceReferenceId(), source) != null) {
                throw invalid("duplicate authorized source reference");
            }
        }
        Set<String> candidateIds = new HashSet<>();
        for (var candidate : discovery.candidates()) {
            requireText(candidate.id());
            requireText(candidate.constituentMeaning());
            if (!Set.of("COMPLETE", "CONSTITUENT")
                    .contains(candidate.relationToExpectedConcept())) {
                throw invalid("candidate relation to expected concept is invalid");
            }
            if (!candidateIds.add(candidate.id())
                    || candidate.expectedConceptIndex() < 0
                    || candidate.expectedConceptIndex() >= input.expectedConcepts().size()
                    || candidate.assertionIds().isEmpty()) {
                throw invalid("invalid candidate identity or index");
            }
            Set<String> linked = new HashSet<>();
            for (String assertionId : candidate.assertionIds()) {
                var assertion = assertions.get(assertionId);
                if (assertion == null || !linked.add(assertionId)
                        || !assertion.relatedExpectedConceptIndexes()
                                .contains(candidate.expectedConceptIndex())) {
                    throw invalid("candidate has an invalid student assertion link");
                }
            }
            for (var span : candidate.sourceEvidence()) {
                UUID referenceId = canonicalUuid(span.sourceReferenceId());
                AuthorizedResponseEvidence.Source source = sources.get(referenceId);
                if (source == null
                        || !source.materialVersionId().equals(canonicalUuid(span.sourceVersion()))) {
                    throw invalid("candidate cites an unauthorized or stale source");
                }
                requireSpan(source.content(), span.sourceStart(), span.sourceEnd(),
                        span.sourceText());
            }
        }
        discovery.limitations().forEach(ResponseAttributionValidator::requireText);
    }

    public void validateVerification(ResponseEvaluationInput input,
            AuthorizedResponseEvidence evidence, ResponseAttributionDiscovery discovery,
            ResponseAttributionVerification verification) {
        Objects.requireNonNull(verification);
        Map<String, ResponseAttributionDiscovery.Assertion> discoveredAssertions = new HashMap<>();
        discovery.assertions().forEach(assertion ->
                discoveredAssertions.put(assertion.id(), assertion));
        Set<String> assertionIds = discoveredAssertions.keySet();
        Map<String, ResponseAttributionVerification.Disposition> assertionDisposition =
                new HashMap<>();
        for (var item : verification.assertions()) {
            requireText(item.reason());
            if (!assertionIds.contains(item.assertionId())
                    || item.disposition() == null
                    || assertionDisposition.putIfAbsent(item.assertionId(),
                            item.disposition()) != null) {
                throw invalid("assertion verification coverage is invalid");
            }
            if (item.fidelity() != ResponseAttributionVerification.Fidelity.FAITHFUL) {
                throw invalid("student assertion meaning is not verified against the response");
            }
        }
        if (!assertionDisposition.keySet().equals(assertionIds)) {
            throw invalid("assertion verification is incomplete");
        }
        Map<String, ResponseAttributionDiscovery.Candidate> candidates = new HashMap<>();
        discovery.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        Map<String, ResponseAttributionVerification.Disposition> candidateDisposition =
                new HashMap<>();
        for (var item : verification.candidates()) {
            requireText(item.reason());
            var candidate = candidates.get(item.candidateId());
            if (candidate == null || item.disposition() == null
                    || candidateDisposition.putIfAbsent(item.candidateId(),
                            item.disposition()) != null) {
                throw invalid("candidate verification coverage is invalid");
            }
            if (item.disposition() == ResponseAttributionVerification.Disposition.SUPPORTED
                    && (candidate.sourceEvidence().isEmpty()
                            || candidate.assertionIds().stream().anyMatch(id ->
                                    assertionDisposition.get(id)
                                            != ResponseAttributionVerification.Disposition.SUPPORTED)
                            || candidate.assertionIds().stream().allMatch(id ->
                                    referentialOnly(discoveredAssertions.get(id).studentText())))) {
                throw invalid("supported candidate lacks supported assertion or source");
            }
        }
        if (!candidateDisposition.keySet().equals(candidates.keySet())) {
            throw invalid("candidate verification is incomplete");
        }
        Map<Integer, ResponseAttributionVerification.Coverage> coverage = new HashMap<>();
        for (var item : verification.expectedConceptCoverage()) {
            requireText(item.reason());
            if (item.coverage() == null || item.expectedConceptIndex() < 0
                    || item.expectedConceptIndex() >= input.expectedConcepts().size()
                    || coverage.putIfAbsent(item.expectedConceptIndex(),
                            item.coverage()) != null) {
                throw invalid("concept verification coverage is invalid");
            }
        }
        if (coverage.size() != input.expectedConcepts().size()) {
            throw invalid("concept verification is incomplete");
        }
        Set<String> misconceptions = new HashSet<>();
        for (var misconception : verification.misconceptions()) {
            requireText(misconception.description());
            if (assertionDisposition.get(misconception.assertionId())
                    != ResponseAttributionVerification.Disposition.UNSUPPORTED
                    || !misconceptions.add(misconception.assertionId())) {
                throw invalid("misconception lacks a distinct disproven student assertion");
            }
        }
        for (var assertion : discovery.assertions()) {
            if (assertionDisposition.get(assertion.id())
                    == ResponseAttributionVerification.Disposition.UNSUPPORTED
                    && !"UNCERTAIN".equals(assertion.polarity())
                    && !misconceptions.contains(assertion.id())) {
                throw invalid("disproven assertion has no misconception correction");
            }
        }
        for (var item : verification.expectedConceptCoverage()) {
            int index = item.expectedConceptIndex();
            boolean fullCandidate = discovery.candidates().stream().anyMatch(candidate ->
                    candidate.expectedConceptIndex() == index
                            && candidateDisposition.get(candidate.id())
                                    == ResponseAttributionVerification.Disposition.SUPPORTED
                            && "COMPLETE".equals(candidate.relationToExpectedConcept()));
            if (item.coverage() != ResponseAttributionVerification.Coverage.COMPLETE) {
                if (fullCandidate) {
                    throw invalid("verified complete relation conflicts with missing coverage");
                }
                continue;
            }
            boolean contradicted = discovery.assertions().stream().anyMatch(assertion ->
                    assertion.relatedExpectedConceptIndexes().contains(index)
                            && assertionDisposition.get(assertion.id())
                                    != ResponseAttributionVerification.Disposition.SUPPORTED);
            if (!fullCandidate || contradicted) {
                throw invalid("complete concept conflicts with student assertions or candidates");
            }
        }
        for (var candidate : discovery.candidates()) {
            if (candidateDisposition.get(candidate.id())
                    == ResponseAttributionVerification.Disposition.SUPPORTED
                    && coverage.get(candidate.expectedConceptIndex())
                            == ResponseAttributionVerification.Coverage.AMBIGUOUS) {
                throw invalid("supported candidate conflicts with ambiguous coverage");
            }
        }
        if (verification.complete() && verification.limitations().isEmpty()
                && verification.assertions().stream().noneMatch(item ->
                        item.disposition() == ResponseAttributionVerification.Disposition.AMBIGUOUS)
                && verification.candidates().stream().noneMatch(item ->
                        item.disposition() == ResponseAttributionVerification.Disposition.AMBIGUOUS)
                && verification.expectedConceptCoverage().stream().noneMatch(item ->
                        item.coverage() == ResponseAttributionVerification.Coverage.AMBIGUOUS)) {
            if (verification.mechanismEvidence() == null) {
                throw invalid("verified feedback mechanism lacks source evidence");
            }
            validateSourceSpan(evidence, verification.mechanismEvidence());
            if (verification.mechanismEvidence().sourceText().length() > 600
                    || verification.mechanismEvidence().sourceText().strip().length() < 20) {
                throw invalid("verified feedback mechanism is not a concise source excerpt");
            }
        }
        verification.limitations().forEach(ResponseAttributionValidator::requireText);
    }

    private static void validateSourceSpan(AuthorizedResponseEvidence evidence,
            ResponseAttributionDiscovery.SourceSpan span) {
        UUID referenceId = canonicalUuid(span.sourceReferenceId());
        AuthorizedResponseEvidence.Source source = evidence.sources().stream()
                .filter(item -> item.sourceReferenceId().equals(referenceId))
                .findFirst().orElseThrow(() -> invalid("feedback cites an unauthorized source"));
        if (!source.materialVersionId().equals(canonicalUuid(span.sourceVersion()))) {
            throw invalid("feedback cites a stale source");
        }
        requireSpan(source.content(), span.sourceStart(), span.sourceEnd(), span.sourceText());
    }

    private static void requireCompleteIndexes(List<Integer> values, int expectedCount) {
        if (values.size() != expectedCount) {
            throw invalid("expected concept indexes are incomplete");
        }
        requireDistinctValidIndexes(values, expectedCount);
    }

    private static void requireDistinctValidIndexes(List<Integer> values, int expectedCount) {
        Set<Integer> distinct = new HashSet<>();
        for (Integer value : values) {
            if (value == null || value < 0 || value >= expectedCount || !distinct.add(value)) {
                throw invalid("expected concept index is invalid");
            }
        }
    }

    private static void requireSpan(String original, int start, int end, String exact) {
        if (exact == null || exact.isBlank() || start < 0 || end > original.length()
                || start >= end || splitsSurrogate(original, start)
                || splitsSurrogate(original, end)
                || !original.substring(start, end).equals(exact)) {
            throw invalid("evidence span is not an exact UTF-16 substring");
        }
    }

    private static void requireStudentContentCoverage(String response,
            List<ResponseAttributionDiscovery.Assertion> assertions) {
        var boundaries = new TreeSet<Integer>();
        boundaries.add(0);
        boundaries.add(response.length());
        for (int offset = 0; offset < response.length(); offset++) {
            if (".,;:!?\r\n".indexOf(response.charAt(offset)) >= 0) {
                boundaries.add(offset + 1);
            }
        }
        var clauses = CLAUSE_BOUNDARY.matcher(response);
        while (clauses.find()) {
            boundaries.add(clauses.start());
        }
        var points = new ArrayList<>(boundaries);
        for (int segment = 0; segment < points.size() - 1; segment++) {
            int start = points.get(segment);
            int end = points.get(segment + 1);
            boolean[] covered = new boolean[end - start];
            for (var assertion : assertions) {
                if (assertion.studentStart() >= start && assertion.studentEnd() <= end) {
                    for (int offset = assertion.studentStart(); offset < assertion.studentEnd(); offset++) {
                        covered[offset - start] = true;
                    }
                }
            }
            for (int offset = start; offset < end; ) {
                int codePoint = response.codePointAt(offset);
                if (!Character.isLetterOrDigit(codePoint)) {
                    offset += Character.charCount(codePoint);
                    continue;
                }
                int tokenStart = offset;
                boolean represented = true;
                do {
                    represented &= covered[offset - start];
                    offset += Character.charCount(codePoint);
                    if (offset == end) {
                        break;
                    }
                    codePoint = response.codePointAt(offset);
                } while (Character.isLetterOrDigit(codePoint));
                if (!represented && !INCIDENTAL_WORDS.contains(
                        response.substring(tokenStart, offset).toLowerCase(Locale.ROOT))) {
                    throw invalid("student response content is missing from assertion discovery");
                }
            }
        }
    }

    private static boolean splitsSurrogate(String text, int offset) {
        return offset > 0 && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset));
    }

    private static boolean referentialOnly(String studentText) {
        return studentText.strip().matches(
                "(?iu)(?:it|this|that|they|the nerve|the injury|the lesion)[\\p{Punct}]*");
    }

    private static UUID canonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid("source ID is not canonical");
            }
            return parsed;
        } catch (RuntimeException invalid) {
            throw invalid("source ID is invalid");
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("attribution field is blank");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Limits(int maxResponseCharacters, int maxSources,
            int maxSourceCharacters, int maxAssertions, int maxCandidates,
            int maxSourceSpans, long maxEndToEndMillis) {
        public Limits {
            if (maxResponseCharacters < 1 || maxSources < 1 || maxSourceCharacters < 1
                    || maxAssertions < 1 || maxCandidates < 1 || maxSourceSpans < 1
                    || maxEndToEndMillis < 1) {
                throw new IllegalArgumentException("attribution limits must be positive");
            }
        }
    }
}
