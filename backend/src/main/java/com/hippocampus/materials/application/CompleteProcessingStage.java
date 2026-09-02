package com.hippocampus.materials.application;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;

public class CompleteProcessingStage {
    private final ProcessingJobStageCompletionRepository jobs;

    public CompleteProcessingStage(ProcessingJobStageCompletionRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public void execute(ClaimedProcessingJob job, ProcessingStageResult result) {
        if (job.jobType() != result.executedStage()) {
            throw new IllegalArgumentException("Executed stage does not match the claimed job");
        }
        ProcessingJobType nextDurableStage =
                ProcessingStageSequence.nextDurablePhaseThreeStage(result.executedStage());
        if (!jobs.completeSuccessfulStage(job.jobId(), result.executedStage(), nextDurableStage)) {
            throw new ProcessingStageCompletionException(job.jobId(), result.executedStage());
        }
    }
}
