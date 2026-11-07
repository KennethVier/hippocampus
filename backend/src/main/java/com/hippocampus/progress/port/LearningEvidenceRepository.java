package com.hippocampus.progress.port;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.progress.domain.EvidenceProjectionKey;
import com.hippocampus.progress.domain.LearningEvidence;

public interface LearningEvidenceRepository {
    LearningEvidence lockOrCreate(EvidenceProjectionKey projectionKey, UUID initialId, Instant persistedAt);

    LearningEvidence save(LearningEvidence evidence);
}
