package com.hippocampus.learning.port;

public final class DuplicateSubjectNameException extends RuntimeException {
    public DuplicateSubjectNameException(Throwable cause) {
        super("A subject name conflicts with an existing subject", cause);
    }
}
