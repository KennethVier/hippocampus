package com.hippocampus.materials.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.port.DocumentStructureRepository;

public final class JpaDocumentStructureRepository implements DocumentStructureRepository {
    private final SpringDataDocumentNodeRepository nodes;
    private final SpringDataTextBlockRepository blocks;

    public JpaDocumentStructureRepository(
            SpringDataDocumentNodeRepository nodes,
            SpringDataTextBlockRepository blocks) {
        this.nodes = nodes;
        this.blocks = blocks;
    }

    @Override
    public List<DocumentNode> findNodesByMaterialVersion(UUID materialVersionId) {
        return nodes.findByMaterialVersionIdOrderByOrdinalAsc(materialVersionId).stream()
                .map(JpaDocumentStructureRepository::toDomain)
                .toList();
    }

    @Override
    public List<DocumentNode> findChildren(UUID materialVersionId, UUID parentId) {
        return nodes.findByMaterialVersionIdAndParentIdOrderByOrdinalAsc(materialVersionId, parentId).stream()
                .map(JpaDocumentStructureRepository::toDomain)
                .toList();
    }

    @Override
    public List<TextBlock> findTextBlocksByOrdinalRange(
            UUID materialVersionId, int firstOrdinal, int lastOrdinal) {
        if (firstOrdinal < 1 || lastOrdinal < firstOrdinal) {
            throw new IllegalArgumentException("Text block ordinal range must be positive and ordered");
        }
        return blocks.findByMaterialVersionIdAndOrdinalBetweenOrderByOrdinalAsc(
                        materialVersionId, firstOrdinal, lastOrdinal)
                .stream()
                .map(JpaDocumentStructureRepository::toDomain)
                .toList();
    }

    private static DocumentNode toDomain(DocumentNodeEntity node) {
        return new DocumentNode(
                node.getId(), node.getMaterialVersionId(), node.getParentId(), node.getNodeType(), node.getTitle(),
                node.getOrdinal(), node.getStartPage(), node.getEndPage(), node.getStartOffset(), node.getEndOffset(),
                node.getDetectionOrigin(), node.getDetectionConfidence(), node.getCreatedAt());
    }

    private static TextBlock toDomain(TextBlockEntity block) {
        return new TextBlock(
                block.getId(), block.getMaterialVersionId(), block.getDocumentNodeId(), block.getPageNumber(),
                block.getBlockType(), block.getOrdinal(), block.getContent(), block.getExtractionMethod(),
                block.getQuality(), block.getCreatedAt());
    }
}
