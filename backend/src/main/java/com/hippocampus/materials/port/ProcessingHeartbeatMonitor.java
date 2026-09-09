package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public interface ProcessingHeartbeatMonitor {
    Heartbeat start(ClaimedProcessingJob job);
    interface Heartbeat extends AutoCloseable {
        void verifyOwnership();
        @Override void close();
    }
}
