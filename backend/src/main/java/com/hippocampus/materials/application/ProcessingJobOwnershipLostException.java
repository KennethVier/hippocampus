package com.hippocampus.materials.application;

public final class ProcessingJobOwnershipLostException extends IllegalStateException {
    public ProcessingJobOwnershipLostException() { super("Processing job ownership was lost"); }
}
