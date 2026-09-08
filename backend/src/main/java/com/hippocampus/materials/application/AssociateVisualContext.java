package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.VisualContextAsset;
import com.hippocampus.materials.domain.VisualContextAssociation;
import com.hippocampus.materials.domain.VisualContextAssociationPolicy;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.VisualContextRepository;

public class AssociateVisualContext {
    private final VisualContextRepository visuals;
    private final DocumentStructureRepository structures;
    private final VisualContextAssociationPolicy policy;

    public AssociateVisualContext(
            VisualContextRepository visuals,
            DocumentStructureRepository structures,
            VisualContextAssociationPolicy policy) {
        this.visuals = Objects.requireNonNull(visuals);
        this.structures = Objects.requireNonNull(structures);
        this.policy = Objects.requireNonNull(policy);
    }

    @Transactional
    public List<VisualContextAssociation> execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "job must not be null");
        if (job.jobType() != ProcessingJobType.VISUAL_EXTRACT || job.materialVersionId() == null) {
            throw new IllegalArgumentException("A VISUAL_EXTRACT job with a material version is required");
        }
        UUID materialVersionId = job.materialVersionId();
        List<VisualContextAsset> assets = visuals.findByMaterialVersion(materialVersionId);
        if (assets.isEmpty()) {
            return List.of();
        }

        Set<UUID> nodeIds = new HashSet<>();
        for (DocumentNode node : structures.findNodesByMaterialVersion(materialVersionId)) {
            if (!materialVersionId.equals(node.materialVersionId()) || !nodeIds.add(node.id())) {
                throw new IllegalStateException("Document hierarchy is inconsistent for visual context association");
            }
        }
        if (nodeIds.isEmpty() || assets.stream().anyMatch(asset -> !nodeIds.contains(asset.documentNodeId()))) {
            throw new IllegalStateException("Visual context references an incompatible document node");
        }

        List<TextBlock> pageText = new ArrayList<>();
        assets.stream().map(VisualContextAsset::pageNumber).distinct().sorted()
                .forEach(page -> pageText.addAll(
                        structures.findTextBlocksByOrdinalRange(materialVersionId, page, page)));
        List<VisualContextAssociation> associations = policy.associate(materialVersionId, assets, pageText);
        visuals.persist(materialVersionId, associations);
        return associations;
    }
}
