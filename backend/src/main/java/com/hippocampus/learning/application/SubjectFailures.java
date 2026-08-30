package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.DomainConflictException;
import com.hippocampus.shared.domain.error.ErrorCode;

final class SubjectFailures {
    private static final ErrorCode NOT_FOUND = new ErrorCode("SUBJECT_NOT_FOUND");
    private static final ErrorCode NAME_CONFLICT = new ErrorCode("SUBJECT_NAME_CONFLICT");

    private SubjectFailures() {}

    static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(NOT_FOUND, "Subject was not found.");
    }

    static DomainConflictException nameConflict() {
        return new DomainConflictException(NAME_CONFLICT, "A subject with this name already exists.");
    }
}
