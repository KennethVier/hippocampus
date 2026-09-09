package com.hippocampus.materials.application;

import java.util.UUID;
import com.hippocampus.materials.domain.ProcessingJobType;

public sealed interface ProcessingRunResult {
    record NoWork() implements ProcessingRunResult {}
    record Completed(UUID jobId, ProcessingJobType stage) implements ProcessingRunResult {}
    record Failed(UUID jobId, ProcessingJobType stage, String errorCode) implements ProcessingRunResult {}
}
