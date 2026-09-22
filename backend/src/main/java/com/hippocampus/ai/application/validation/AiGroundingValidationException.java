package com.hippocampus.ai.application.validation;

import java.util.Objects;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class AiGroundingValidationException extends ApplicationException {

    private static final ErrorCode ERROR_CODE = new ErrorCode("AI_GROUNDING_FAILURE");
    private static final String CLIENT_MESSAGE = "AI output grounding could not be validated";

    private final Reason reason;

    public AiGroundingValidationException(Reason reason) {
        super(ERROR_CODE, CLIENT_MESSAGE);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_REFERENCE,
        DUPLICATE_REFERENCE,
        REFERENCE_NOT_IN_PROMPT,
        EVIDENCE_PROVENANCE_MISMATCH,
        AUTHORIZATION_NOT_CONFIRMED,
        CURRENT_PROVENANCE_MISMATCH,
        UNSUPPORTED_RESULT_TYPE
    }
}
