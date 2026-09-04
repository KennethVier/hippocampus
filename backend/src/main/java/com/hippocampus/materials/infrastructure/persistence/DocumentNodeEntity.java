package com.hippocampus.materials.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_nodes")
public class DocumentNodeEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "material_version_id", nullable = false, updatable = false)
    private UUID materialVersionId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private DocumentNodeType nodeType;

    @Column(name = "title")
    private String title;

    @Column(name = "ordinal")
    private Integer ordinal;

    @Column(name = "start_page")
    private Integer startPage;

    @Column(name = "end_page")
    private Integer endPage;

    @Column(name = "start_offset")
    private Long startOffset;

    @Column(name = "end_offset")
    private Long endOffset;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_origin", nullable = false)
    private DocumentNodeDetectionOrigin detectionOrigin;

    @Column(name = "detection_confidence")
    private String detectionConfidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentNodeEntity() {}

    public UUID getId() { return id; }
    public UUID getMaterialVersionId() { return materialVersionId; }
    public UUID getParentId() { return parentId; }
    public DocumentNodeType getNodeType() { return nodeType; }
    public String getTitle() { return title; }
    public Integer getOrdinal() { return ordinal; }
    public Integer getStartPage() { return startPage; }
    public Integer getEndPage() { return endPage; }
    public Long getStartOffset() { return startOffset; }
    public Long getEndOffset() { return endOffset; }
    public DocumentNodeDetectionOrigin getDetectionOrigin() { return detectionOrigin; }
    public String getDetectionConfidence() { return detectionConfidence; }
    public Instant getCreatedAt() { return createdAt; }
}
