package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PersistVisualContext persistence;

    public AssociateVisualContext(
            VisualContextRepository visuals,
            DocumentStructureRepository structures,
            VisualContextAssociationPolicy policy,
            PersistVisualContext persistence) {
        this.visuals = Objects.requireNonNull(visuals);
        this.structures = Objects.requireNonNull(structures);
        this.policy = Objects.requireNonNull(policy);
        this.persistence = Objects.requireNonNull(persistence);
    }

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

        Set<UUID> assetIds = new HashSet<>();
        for (VisualContextAsset asset : assets) {
            if (!materialVersionId.equals(asset.materialVersionId())) {
                throw new IllegalStateException("Visual context contains a cross-material-version asset");
            }
            if (!assetIds.add(asset.id())) {
                throw new IllegalStateException("Visual context contains a duplicate asset");
            }
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

        Map<Integer, List<VisualContextAsset>> assetsByPage = assets.stream()
                .collect(Collectors.groupingBy(VisualContextAsset::pageNumber));
        List<VisualContextAssociation> result = new ArrayList<>();
        assetsByPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> associatePage(materialVersionId, entry.getKey(), entry.getValue(), result));
        return List.copyOf(result);
    }

    private void associatePage(
            UUID materialVersionId,
            int pageNumber,
            List<VisualContextAsset> pageAssets,
            List<VisualContextAssociation> result) {
        List<TextBlock> pageText = structures.findTextBlocksByOrdinalRange(
                materialVersionId, pageNumber, pageNumber);
        List<VisualContextAssociation> associations = policy.associate(
                materialVersionId,
                pageAssets.stream().sorted(Comparator.comparing(VisualContextAsset::id)).toList(),
                pageText);
        if (!associations.isEmpty()) {
            persistence.execute(materialVersionId, associations);
            result.addAll(associations);
        }
    }
}
