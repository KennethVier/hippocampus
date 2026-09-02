package com.hippocampus.materials.domain;

public enum ProcessingJobStatus {
    PENDING,
    RUNNING,
    RETRY,
    COMPLETED,
    FAILED,
    CANCELLED
}
