package com.hippocampus.materials.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "text_blocks")
public class TextBlockEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "material_version_id", nullable = false, updatable = false)
    private UUID materialVersionId;

    @Column(name = "document_node_id")
    private UUID documentNodeId;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false)
    private TextBlockType blockType;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", nullable = false)
    private TextBlockExtractionMethod extractionMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality")
    private TextBlockQuality quality;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TextBlockEntity() {}

    public UUID getId() { return id; }
    public UUID getMaterialVersionId() { return materialVersionId; }
    public UUID getDocumentNodeId() { return documentNodeId; }
    public Integer getPageNumber() { return pageNumber; }
    public TextBlockType getBlockType() { return blockType; }
    public int getOrdinal() { return ordinal; }
    public String getContent() { return content; }
    public TextBlockExtractionMethod getExtractionMethod() { return extractionMethod; }
    public TextBlockQuality getQuality() { return quality; }
    public Instant getCreatedAt() { return createdAt; }
}
