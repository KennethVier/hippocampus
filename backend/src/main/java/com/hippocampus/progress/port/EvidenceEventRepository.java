package com.hippocampus.progress.port;

import java.util.List;

import com.hippocampus.progress.domain.EvidenceEvent;
import com.hippocampus.progress.domain.EvidenceProjectionKey;

public interface EvidenceEventRepository {
    EvidenceEvent append(EvidenceEvent event);

    List<EvidenceEvent> findByProjectionKey(EvidenceProjectionKey projectionKey);
}
