package com.hippocampus.materials.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "material_versions")
public class MaterialVersionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @Column(name = "processing_progress", precision = 5, scale = 2)
    private BigDecimal processingProgress;

    @Column(name = "extraction_method")
    private String extractionMethod;

    @Column(name = "extraction_quality")
    private String extractionQuality;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MaterialVersionEntity() {}

    public MaterialVersionEntity(UUID materialId, int versionNumber, String processingStatus) {
        this.materialId = materialId;
        this.versionNumber = versionNumber;
        this.processingStatus = processingStatus;
    }

    public UUID getId() { return id; }
    public UUID getMaterialId() { return materialId; }
    public int getVersionNumber() { return versionNumber; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public BigDecimal getProcessingProgress() { return processingProgress; }
    public void setProcessingProgress(BigDecimal processingProgress) { this.processingProgress = processingProgress; }
    public String getExtractionMethod() { return extractionMethod; }
    public void setExtractionMethod(String extractionMethod) { this.extractionMethod = extractionMethod; }
    public String getExtractionQuality() { return extractionQuality; }
    public void setExtractionQuality(String extractionQuality) { this.extractionQuality = extractionQuality; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
