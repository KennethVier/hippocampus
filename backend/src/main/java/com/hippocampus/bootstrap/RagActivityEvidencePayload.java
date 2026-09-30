package com.hippocampus.bootstrap;

import java.util.Objects;

import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.rag.domain.EvidencePackage;

public final class RagActivityEvidencePayload implements ActivityEvidencePort.EvidencePayload {

    private final EvidencePackage evidencePackage;

    public RagActivityEvidencePayload(EvidencePackage evidencePackage) {
        this.evidencePackage = Objects.requireNonNull(
                evidencePackage, "evidencePackage must not be null");
    }

    EvidencePackage evidencePackage() {
        return evidencePackage;
    }
}
