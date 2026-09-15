package com.hippocampus.rag.application;

import java.util.List;
import java.util.Objects;

import com.hippocampus.materials.application.CreateSourceReferences;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.domain.VisualSourceTarget;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;

public final class MaterializeEvidenceSourceReferences {
    private final CreateSourceReferences createSourceReferences;

    public MaterializeEvidenceSourceReferences(CreateSourceReferences createSourceReferences) {
        this.createSourceReferences = Objects.requireNonNull(createSourceReferences);
    }

    public List<SourceReference> execute(EvidencePackage evidencePackage) {
        Objects.requireNonNull(evidencePackage, "evidencePackage must not be null");
        List<SourceReferenceTarget> targets = evidencePackage.sourceReferences().stream()
                .map(MaterializeEvidenceSourceReferences::target)
                .toList();
        return createSourceReferences.execute(new CreateSourceReferences.Command(targets));
    }

    private static SourceReferenceTarget target(EvidenceSourceReference reference) {
        if (reference.kind() == EvidenceReferenceKind.CHUNK) {
            return new ChunkSourceTarget(
                    reference.materialId(), reference.materialVersionId(), reference.chunkId());
        }
        return new VisualSourceTarget(
                reference.materialId(), reference.materialVersionId(), reference.visualId());
    }
}
