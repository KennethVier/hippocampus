package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.ProcessingHeartbeatMonitor;

public final class ExecuteClaimedProcessingJob {
    private final ProcessingDispatcher dispatcher;
    private final CompleteProcessingStage completion;
    private final ProcessingFailureClassifier classifier;
    private final FinalizeProcessingFailure failureFinalizer;
    private final ProcessingHeartbeatMonitor heartbeats;

    /** Convenience for isolated dispatcher tests; production wiring always supplies recovery collaborators. */
    public ExecuteClaimedProcessingJob(ProcessingDispatcher dispatcher, CompleteProcessingStage completion) {
        this.dispatcher = dispatcher;
        this.completion = completion;
        this.classifier = null;
        this.failureFinalizer = null;
        this.heartbeats = null;
    }

    public ExecuteClaimedProcessingJob(
            ProcessingDispatcher dispatcher,
            CompleteProcessingStage completion,
            ProcessingFailureClassifier classifier,
            FinalizeProcessingFailure failureFinalizer,
            ProcessingHeartbeatMonitor heartbeats) {
        this.dispatcher = dispatcher;
        this.completion = completion;
        this.classifier = classifier;
        this.failureFinalizer = failureFinalizer;
        this.heartbeats = heartbeats;
    }

    public ProcessingStageResult execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "Claimed processing job must not be null");
        if (heartbeats == null) {
            ProcessingStageResult result = dispatcher.dispatch(job);
            completion.execute(job, result);
            return result;
        }
        try (ProcessingHeartbeatMonitor.Heartbeat heartbeat = heartbeats.start(job)) {
            try {
                ProcessingStageResult result = dispatcher.dispatch(job);
                heartbeat.verifyOwnership();
                completion.execute(job, result);
                return result;
            } catch (ProcessingJobOwnershipLostException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                ProcessingFailure classified = classifier.classify(failure);
                failureFinalizer.execute(job, classified);
                throw failure;
            }
        }
    }
}
