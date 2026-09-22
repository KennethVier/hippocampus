package com.hippocampus.ai.application.validation;

import java.util.Objects;

import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class AiSchemaValidationException extends ApplicationException {

    private static final ErrorCode ERROR_CODE = new ErrorCode("AI_SCHEMA_FAILURE");
    private static final String CLIENT_MESSAGE = "AI output did not match the required schema";

    private final AiOutputContract outputContract;
    private final Reason reason;

    public AiSchemaValidationException(AiOutputContract outputContract, Reason reason) {
        super(ERROR_CODE, CLIENT_MESSAGE);
        this.outputContract = Objects.requireNonNull(outputContract, "outputContract must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public AiOutputContract outputContract() {
        return outputContract;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MALFORMED_JSON,
        CONTRACT_MISMATCH,
        BUSINESS_RULE_VIOLATION
    }
}
