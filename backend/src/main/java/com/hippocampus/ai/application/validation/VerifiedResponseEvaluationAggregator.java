package com.hippocampus.ai.application.validation;

import java.util.List;
import java.util.Objects;
import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.learning.port.ResponseEvaluationPort;

/** ADR-0018: model attribution labels have no independent medical grading authority. */
public final class VerifiedResponseEvaluationAggregator {
    public ResponseEvaluationPort.Result aggregate(ResponseEvaluationInput input,
            ResponseAttributionDiscovery discovery, ResponseAttributionVerification verification) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(discovery);
        Objects.requireNonNull(verification);
        return uncertain();
    }
    public static ResponseEvaluationPort.Result uncertain() {
        return new ResponseEvaluationPort.Result(ResponseEvaluationPort.Outcome.UNCERTAIN,
                List.of(), List.of(), List.of(), "The response could not be evaluated reliably.",
                ResponseEvaluationPort.Certainty.LIMITED, ResponseEvaluationPort.RecommendedAction.RETRY, true);
    }
}
