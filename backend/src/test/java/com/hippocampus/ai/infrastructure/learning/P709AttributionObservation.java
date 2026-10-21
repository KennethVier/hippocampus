package com.hippocampus.ai.infrastructure.learning;

import com.hippocampus.ai.application.validation.ResponseAttributionValidator;
import com.hippocampus.ai.domain.*;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import java.util.List;

/** Test-only replay. These values are NOT the adapter's internal returned values or medical proof. */
public final class P709AttributionObservation {
    private P709AttributionObservation() {}
    public record Observation(Object normalizedDiscovery, Object normalizedVerification,
            List<?> discoveryCorrections, List<?> verificationCorrections, String structuralStatus,
            String provenance) {}

    public static ResponseAttributionDiscovery discoveryReplay(ResponseEvaluationInput input,
            AuthorizedResponseEvidence evidence, ResponseAttributionDiscovery raw) {
        var normalized = SourceSpanOffsetNormalizer.discovery(raw, evidence).value();
        new ResponseAttributionValidator(new ResponseAttributionValidator.Limits(8_000, 8, 32_000,
                32, 64, 128, 60_000)).validateDiscovery(input, evidence, normalized);
        return normalized;
    }

    public static Observation replay(ResponseEvaluationInput input, AuthorizedResponseEvidence evidence,
            ResponseAttributionDiscovery rawDiscovery, ResponseAttributionVerification rawVerification) {
        var d = SourceSpanOffsetNormalizer.discovery(rawDiscovery, evidence);
        var validator = new ResponseAttributionValidator(new ResponseAttributionValidator.Limits(
                8_000, 8, 32_000, 32, 64, 128, 60_000));
        validator.validateDiscovery(input, evidence, d.value());
        if (!d.value().complete() || rawVerification == null)
            throw new IllegalArgumentException("incomplete observation trace");
        var v = SourceSpanOffsetNormalizer.verification(rawVerification, evidence);
        validator.validateVerification(input, evidence, d.value(), v.value());
        if (!v.value().complete()) throw new IllegalArgumentException("incomplete verification trace");
        return new Observation(d.value(), v.value(), d.corrections(), v.corrections(), "PASS",
                "TEST_ONLY_NORMALIZATION_REPLAY_NOT_ADAPTER_OUTPUT_NOT_MEDICAL_PROOF");
    }
}
