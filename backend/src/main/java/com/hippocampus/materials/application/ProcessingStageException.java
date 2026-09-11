package com.hippocampus.materials.application;

/** Exception thrown by a processing stage handler when a durable failure occurs. */
public class ProcessingStageException extends RuntimeException {
    public ProcessingStageException(String message) {
        super(message);
    }

    public ProcessingStageException(String message, Throwable cause) {
        super(message, cause);
    }
}
