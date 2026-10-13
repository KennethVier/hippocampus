package com.hippocampus.ai.infrastructure.learning;

import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Corrects only unambiguous offsets in a specifically authorized source. */
final class SourceSpanOffsetNormalizer {
    record Correction(String sourceReferenceId, String sourceVersion,
            int originalStart, int originalEnd, int correctedStart, int correctedEnd) {}

    record Normalized<T>(T value, List<Correction> corrections) {
        Normalized { corrections = List.copyOf(corrections); }
    }

    private SourceSpanOffsetNormalizer() {}

    static Normalized<ResponseAttributionDiscovery> discovery(
            ResponseAttributionDiscovery discovery, AuthorizedResponseEvidence evidence) {
        List<Correction> corrections = new ArrayList<>();
        List<ResponseAttributionDiscovery.Candidate> candidates = new ArrayList<>();
        for (var candidate : discovery.candidates()) {
            List<ResponseAttributionDiscovery.SourceSpan> spans = new ArrayList<>();
            for (var span : candidate.sourceEvidence()) {
                spans.add(normalize(span, evidence, corrections));
            }
            candidates.add(new ResponseAttributionDiscovery.Candidate(
                    candidate.id(), candidate.assertionIds(), candidate.expectedConceptIndex(),
                    candidate.constituentMeaning(), candidate.relationToExpectedConcept(), spans));
        }
        return new Normalized<>(new ResponseAttributionDiscovery(
                discovery.assertions(), candidates, discovery.coveredExpectedConceptIndexes(),
                discovery.complete(), discovery.limitations()), corrections);
    }

    static Normalized<ResponseAttributionVerification> verification(
            ResponseAttributionVerification verification, AuthorizedResponseEvidence evidence) {
        List<Correction> corrections = new ArrayList<>();
        var mechanism = verification.mechanismEvidence() == null ? null
                : normalize(verification.mechanismEvidence(), evidence, corrections);
        return new Normalized<>(new ResponseAttributionVerification(
                verification.assertions(), verification.candidates(),
                verification.expectedConceptCoverage(), verification.misconceptions(),
                verification.complete(), verification.limitations(), mechanism), corrections);
    }

    private static ResponseAttributionDiscovery.SourceSpan normalize(
            ResponseAttributionDiscovery.SourceSpan span, AuthorizedResponseEvidence evidence,
            List<Correction> corrections) {
        if (span == null || span.sourceReferenceId() == null || span.sourceVersion() == null) {
            throw new IllegalArgumentException("source citation is incomplete");
        }
        UUID referenceId = canonicalUuid(span.sourceReferenceId());
        UUID version = canonicalUuid(span.sourceVersion());
        List<AuthorizedResponseEvidence.Source> matches = evidence.sources().stream()
                .filter(source -> source.sourceReferenceId().equals(referenceId)).toList();
        if (matches.size() != 1 || !matches.getFirst().materialVersionId().equals(version)) {
            throw new IllegalArgumentException("source citation is unauthorized or stale");
        }
        String content = matches.getFirst().content();
        String quote = span.sourceText();
        if (quote == null || quote.isBlank()) {
            throw new IllegalArgumentException("source quotation is absent");
        }
        int start = content.indexOf(quote);
        if (start < 0 || content.indexOf(quote, start + 1) >= 0) {
            throw new IllegalArgumentException("source quotation is absent or ambiguous");
        }
        if (span.sourceStart() >= 0 && span.sourceEnd() <= content.length()
                && span.sourceStart() < span.sourceEnd()
                && content.substring(span.sourceStart(), span.sourceEnd()).equals(quote)) {
            return span;
        }
        int end = start + quote.length();
        corrections.add(new Correction(span.sourceReferenceId(), span.sourceVersion(),
                span.sourceStart(), span.sourceEnd(), start, end));
        return new ResponseAttributionDiscovery.SourceSpan(
                span.sourceReferenceId(), span.sourceVersion(), start, end, quote);
    }

    private static UUID canonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.toString().equals(value)) return parsed;
        } catch (IllegalArgumentException ignored) {
            // Reject malformed source identities below.
        }
        throw new IllegalArgumentException("source citation ID is invalid");
    }
}
