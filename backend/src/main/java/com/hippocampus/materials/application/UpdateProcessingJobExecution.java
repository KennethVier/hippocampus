package com.hippocampus.materials.application;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

public class UpdateProcessingJobExecution {
    private final ProcessingJobExecutionRepository jobs;
    public UpdateProcessingJobExecution(ProcessingJobExecutionRepository jobs) { this.jobs = jobs; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(ClaimedProcessingJob job) { require(jobs.heartbeat(job)); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(ClaimedProcessingJob job, long current, Long total) {
        if (current < 0 || total != null && (total < 0 || current > total)) throw new IllegalArgumentException("Invalid processing progress");
        require(jobs.progress(job, current, total));
    }

    private static void require(boolean updated) { if (!updated) throw new ProcessingJobOwnershipLostException(); }
}
