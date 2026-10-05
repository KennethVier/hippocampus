package com.hippocampus.ai.application.validation;

import java.util.Objects;

final class ResponseEvaluationAggregationException extends IllegalArgumentException {

    private final ResponseEvaluationAggregationFailureReason reason;

    ResponseEvaluationAggregationException(ResponseEvaluationAggregationFailureReason reason) {
        super(Objects.requireNonNull(reason, "reason must not be null").name());
        this.reason = reason;
    }

    ResponseEvaluationAggregationFailureReason reason() {
        return reason;
    }
}
