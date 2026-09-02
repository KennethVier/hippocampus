package com.hippocampus.materials.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class ProcessingDispatcher {
    private final Map<ProcessingJobType, ProcessingStageHandler> handlers;

    public ProcessingDispatcher(List<ProcessingStageHandler> handlers) {
        Objects.requireNonNull(handlers, "Processing stage handlers must not be null");
        EnumMap<ProcessingJobType, ProcessingStageHandler> routes = new EnumMap<>(ProcessingJobType.class);
        for (ProcessingStageHandler handler : handlers) {
            if (handler == null) {
                throw new IllegalArgumentException("Processing stage handler must not be null");
            }
            ProcessingJobType jobType = handler.jobType();
            if (!ProcessingStageSequence.supports(jobType)) {
                throw new UnsupportedProcessingStageException(jobType);
            }
            if (routes.putIfAbsent(jobType, handler) != null) {
                throw new IllegalArgumentException("Duplicate processing stage handler for: " + jobType);
            }
        }
        this.handlers = Map.copyOf(routes);
    }

    public ProcessingStageResult dispatch(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "Claimed processing job must not be null");
        ProcessingJobType jobType = job.jobType();
        if (!ProcessingStageSequence.supports(jobType)) {
            throw new UnsupportedProcessingStageException(jobType);
        }
        if (job.materialVersionId() == null) {
            throw new IllegalArgumentException("Material version is required for processing stage: " + jobType);
        }
        ProcessingStageHandler handler = handlers.get(jobType);
        if (handler == null) {
            throw new MissingProcessingStageHandlerException(jobType);
        }

        handler.handle(job);
        return new ProcessingStageResult(jobType, ProcessingStageSequence.nextStage(jobType));
    }
}
