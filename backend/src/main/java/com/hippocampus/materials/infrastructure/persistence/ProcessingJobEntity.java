package com.hippocampus.materials.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJobEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "material_version_id")
    private UUID materialVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private ProcessingJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingJobStatus status;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "progress", precision = 5, scale = 2)
    private BigDecimal progress;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "processing_version", nullable = false)
    private String processingVersion;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessingJobEntity() {}

    public ProcessingJobEntity(
            UUID userId,
            UUID materialVersionId,
            ProcessingJobType jobType,
            ProcessingJobStatus status,
            int priority,
            BigDecimal progress,
            int attemptCount,
            int maxAttempts,
            String processingVersion) {
        this.userId = userId;
        this.materialVersionId = materialVersionId;
        this.jobType = jobType;
        this.status = status;
        this.priority = priority;
        this.progress = progress;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.processingVersion = processingVersion;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getMaterialVersionId() { return materialVersionId; }
    public ProcessingJobType getJobType() { return jobType; }
    public void setJobType(ProcessingJobType jobType) { this.jobType = jobType; }
    public ProcessingJobStatus getStatus() { return status; }
    public void setStatus(ProcessingJobStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal progress) { this.progress = progress; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public String getProcessingVersion() { return processingVersion; }
    public void setProcessingVersion(String processingVersion) { this.processingVersion = processingVersion; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
