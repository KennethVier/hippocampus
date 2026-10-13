package com.hippocampus.ai.domain;

import java.util.List;

public record ResponseAttributionVerification(
        List<AssertionDisposition> assertions,
        List<CandidateDisposition> candidates,
        List<ConceptCoverage> expectedConceptCoverage,
        List<Misconception> misconceptions,
        boolean complete,
        List<String> limitations,
        ResponseAttributionDiscovery.SourceSpan mechanismEvidence) {
    public ResponseAttributionVerification {
        assertions = List.copyOf(assertions);
        candidates = List.copyOf(candidates);
        expectedConceptCoverage = List.copyOf(expectedConceptCoverage);
        misconceptions = List.copyOf(misconceptions);
        limitations = List.copyOf(limitations);
    }

    public ResponseAttributionVerification(List<AssertionDisposition> assertions,
            List<CandidateDisposition> candidates,
            List<ConceptCoverage> expectedConceptCoverage,
            List<Misconception> misconceptions, boolean complete,
            List<String> limitations) {
        this(assertions, candidates, expectedConceptCoverage, misconceptions,
                complete, limitations, null);
    }

    public record AssertionDisposition(String assertionId, Disposition disposition,
            String reason, Fidelity fidelity) {}
    public record CandidateDisposition(String candidateId, Disposition disposition, String reason) {}
    public record ConceptCoverage(int expectedConceptIndex, Coverage coverage, String reason) {}
    public record Misconception(String assertionId, String description) {}

    public enum Disposition { SUPPORTED, UNSUPPORTED, AMBIGUOUS }
    public enum Fidelity { FAITHFUL, MISMATCH, AMBIGUOUS }
    public enum Coverage { COMPLETE, MISSING, AMBIGUOUS }
}
