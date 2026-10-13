package com.hippocampus.ai.domain;

import java.util.List;

public record ResponseAttributionDiscovery(
        List<Assertion> assertions,
        List<Candidate> candidates,
        List<Integer> coveredExpectedConceptIndexes,
        boolean complete,
        List<String> limitations) {
    public ResponseAttributionDiscovery {
        assertions = List.copyOf(assertions);
        candidates = List.copyOf(candidates);
        coveredExpectedConceptIndexes = List.copyOf(coveredExpectedConceptIndexes);
        limitations = List.copyOf(limitations);
    }

    public record Assertion(
            String id, int studentStart, int studentEnd, String studentText,
            String proposition, String polarity, List<String> qualifiers,
            List<Integer> relatedExpectedConceptIndexes) {
        public Assertion {
            qualifiers = List.copyOf(qualifiers);
            relatedExpectedConceptIndexes = List.copyOf(relatedExpectedConceptIndexes);
        }
    }

    public record Candidate(
            String id, List<String> assertionIds, int expectedConceptIndex,
            String constituentMeaning, String relationToExpectedConcept,
            List<SourceSpan> sourceEvidence) {
        public Candidate {
            assertionIds = List.copyOf(assertionIds);
            sourceEvidence = List.copyOf(sourceEvidence);
        }
    }

    public record SourceSpan(
            String sourceReferenceId, String sourceVersion,
            int sourceStart, int sourceEnd, String sourceText) {}
}
