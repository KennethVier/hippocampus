package com.hippocampus.materials.application;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;

public class CompleteProcessingStage {
    private final ProcessingJobStageCompletionRepository jobs;

    private final DeriveMaterialReadiness readiness;

    public CompleteProcessingStage(ProcessingJobStageCompletionRepository jobs, DeriveMaterialReadiness readiness) {
        this.readiness = readiness;
        this.jobs = jobs;
    }

    @Transactional
    public void execute(ClaimedProcessingJob job, ProcessingStageResult result) {
        if (job.jobType() != result.executedStage()) {
            throw new IllegalArgumentException("Executed stage does not match the claimed job");
        }
        ProcessingJobType nextDurableStage =
                ProcessingStageSequence.nextDurablePhaseThreeStage(result.executedStage());
        if (!jobs.completeSuccessfulStage(job, nextDurableStage)) {
            throw new ProcessingStageCompletionException(job.jobId(), result.executedStage());
        }
        readiness.execute(job.jobId());
    }
}
