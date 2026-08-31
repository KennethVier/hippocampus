package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

final class SubtopicFailures {
    private static final ErrorCode NOT_FOUND = new ErrorCode("SUBTOPIC_NOT_FOUND");
    private SubtopicFailures() {}
    static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(NOT_FOUND, "Subtopic was not found.");
    }
}
