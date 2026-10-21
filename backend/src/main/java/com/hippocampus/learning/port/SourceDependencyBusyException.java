package com.hippocampus.learning.port;

/** A completion authorization dependency could not be locked without waiting. */
public final class SourceDependencyBusyException extends RuntimeException {
    public SourceDependencyBusyException(Throwable cause) {
        super("Source authorization changed concurrently", cause);
    }
}
