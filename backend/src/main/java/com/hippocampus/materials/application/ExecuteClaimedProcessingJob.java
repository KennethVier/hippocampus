package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public final class ExecuteClaimedProcessingJob {
    private final ProcessingDispatcher dispatcher;
    private final CompleteProcessingStage completion;

    public ExecuteClaimedProcessingJob(
            ProcessingDispatcher dispatcher,
            CompleteProcessingStage completion) {
        this.dispatcher = dispatcher;
        this.completion = completion;
    }

    public ProcessingStageResult execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "Claimed processing job must not be null");
        ProcessingStageResult result = dispatcher.dispatch(job);
        completion.execute(job, result);
        return result;
    }
}
