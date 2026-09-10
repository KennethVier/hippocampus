package com.hippocampus.materials.infrastructure.scheduling;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.TaskScheduler;
import com.hippocampus.materials.application.ProcessingJobOwnershipLostException;
import com.hippocampus.materials.application.UpdateProcessingJobExecution;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingHeartbeatMonitor;

public final class SpringProcessingHeartbeatMonitor implements ProcessingHeartbeatMonitor {
    private final TaskScheduler scheduler;
    private final UpdateProcessingJobExecution updates;
    private final Duration interval;
    public SpringProcessingHeartbeatMonitor(TaskScheduler scheduler, UpdateProcessingJobExecution updates, Duration interval) {
        this.scheduler = scheduler; this.updates = updates; this.interval = interval;
    }
    @Override public Heartbeat start(ClaimedProcessingJob job) {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try { updates.heartbeat(job); } catch (RuntimeException exception) { failure.compareAndSet(null, exception); }
        }, interval);
        return new Heartbeat() {
            @Override public void verifyOwnership() {
                RuntimeException exception = failure.get();
                if (exception != null) throw exception;
            }
            @Override public void close() { future.cancel(false); }
        };
    }
}
