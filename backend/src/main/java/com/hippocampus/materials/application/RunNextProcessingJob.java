package com.hippocampus.materials.application;

public final class RunNextProcessingJob {
    private final ClaimNextProcessingJob claims;
    private final ExecuteClaimedProcessingJob execution;
    public RunNextProcessingJob(ClaimNextProcessingJob claims, ExecuteClaimedProcessingJob execution) {
        this.claims = claims; this.execution = execution;
    }
    public boolean execute(String workerId) {
        return claims.execute(workerId).map(job -> { execution.execute(job); return true; }).orElse(false);
    }
}
