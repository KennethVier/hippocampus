package com.hippocampus.materials.application;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

final class SourceReferenceFailures {
    private static final ErrorCode NOT_FOUND = new ErrorCode("SOURCE_REFERENCE_NOT_FOUND");

    private SourceReferenceFailures() {}

    static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(NOT_FOUND, "Source reference was not found.");
    }
}
