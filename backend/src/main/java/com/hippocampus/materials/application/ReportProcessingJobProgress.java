package com.hippocampus.materials.application;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public final class ReportProcessingJobProgress {
    private final UpdateProcessingJobExecution updates;
    public ReportProcessingJobProgress(UpdateProcessingJobExecution updates) { this.updates = updates; }
    public void verifyOwnership(ClaimedProcessingJob job) { updates.heartbeat(job); }
    public void report(ClaimedProcessingJob job, long current, Long total) { updates.progress(job, current, total); }
}
