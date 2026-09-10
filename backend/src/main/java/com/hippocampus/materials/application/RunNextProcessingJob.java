package com.hippocampus.materials.application;

public final class RunNextProcessingJob {
    private final ClaimNextProcessingJob claims;
    private final ExecuteClaimedProcessingJob execution;
    private final ProcessingFailureClassifier failures;
    public RunNextProcessingJob(ClaimNextProcessingJob claims, ExecuteClaimedProcessingJob execution,
            ProcessingFailureClassifier failures) {
        this.claims = claims; this.execution = execution; this.failures = failures;
    }
    public ProcessingRunResult execute(String workerId) {
        return claims.execute(workerId).<ProcessingRunResult>map(job -> {
            try {
                execution.execute(job);
                return new ProcessingRunResult.Completed(job.jobId(), job.jobType());
            } catch (ProcessingJobOwnershipLostException ownershipLost) {
                return new ProcessingRunResult.OwnershipLost(job.jobId(), job.jobType());
            } catch (RuntimeException failure) {
                return new ProcessingRunResult.Failed(
                        job.jobId(), job.jobType(), failures.classify(failure).errorCode());
            }
        }).orElseGet(ProcessingRunResult.NoWork::new);
    }
}
