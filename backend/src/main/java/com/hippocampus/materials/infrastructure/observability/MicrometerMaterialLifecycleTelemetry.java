package com.hippocampus.materials.infrastructure.observability;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.port.MaterialLifecycleTelemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public final class MicrometerMaterialLifecycleTelemetry implements MaterialLifecycleTelemetry {

    static final String UPLOAD_ACCEPTED_METRIC = "hippocampus.materials.upload.accepted";
    static final String UPLOAD_REJECTED_METRIC = "hippocampus.materials.upload.rejected";
    static final String UPLOAD_FAILED_METRIC = "hippocampus.materials.upload.failed";
    static final String MATERIAL_DELETED_METRIC = "hippocampus.materials.deleted";
    static final String STATUS_TRANSITIONS_METRIC = "hippocampus.materials.status.transitions";

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerMaterialLifecycleTelemetry.class);
    private static final String DOMAIN = "materials";
    private static final String MATERIAL = "MATERIAL";
    private static final String MATERIAL_VERSION = "MATERIAL_VERSION";
    private static final String UPLOADED = "UPLOADED";
    private static final String DELETED = "DELETED";
    private static final String TELEMETRY_ERROR_CODE = "MATERIAL_TELEMETRY_PUBLISH_FAILED";

    private final MeterRegistry meterRegistry;

    public MicrometerMaterialLifecycleTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    @Override
    public void uploadAccepted(UUID materialId, UUID materialVersionId) {
        Objects.requireNonNull(materialId);
        Objects.requireNonNull(materialVersionId);
        publishAfterCommitOrNow("upload_accepted", () -> {
            publishSafely("upload_accepted_log", () -> LOG.atInfo()
                    .addKeyValue("event", "material_upload_accepted")
                    .addKeyValue("domain", DOMAIN)
                    .addKeyValue("operation", "upload")
                    .addKeyValue("materialId", materialId)
                    .addKeyValue("materialVersionId", materialVersionId)
                    .addKeyValue("materialStatus", UPLOADED)
                    .addKeyValue("processingStatus", UPLOADED)
                    .log("Material upload accepted"));
            incrementSafely("upload_accepted_metric", UPLOAD_ACCEPTED_METRIC, Tags.empty());
            incrementSafely(
                    "material_uploaded_status_metric",
                    STATUS_TRANSITIONS_METRIC,
                    Tags.of("scope", MATERIAL, "status", UPLOADED));
            incrementSafely(
                    "material_version_uploaded_status_metric",
                    STATUS_TRANSITIONS_METRIC,
                    Tags.of("scope", MATERIAL_VERSION, "status", UPLOADED));
        });
    }

    @Override
    public void uploadRejected(UploadRejectionReason reason) {
        Objects.requireNonNull(reason);
        publishSafely("upload_rejected_log", () -> LOG.atInfo()
                .addKeyValue("event", "material_upload_rejected")
                .addKeyValue("domain", DOMAIN)
                .addKeyValue("operation", "upload")
                .addKeyValue("errorCode", reason.name())
                .log("Material upload rejected"));
        incrementSafely(
                "upload_rejected_metric",
                UPLOAD_REJECTED_METRIC,
                Tags.of("reason", reason.name()));
    }

    @Override
    public void uploadFailed(UploadFailureReason reason) {
        Objects.requireNonNull(reason);
        publishSafely("upload_failed_log", () -> LOG.atError()
                .addKeyValue("event", "material_upload_failed")
                .addKeyValue("domain", DOMAIN)
                .addKeyValue("operation", "upload")
                .addKeyValue("errorCode", reason.name())
                .log("Material upload failed"));
        incrementSafely(
                "upload_failed_metric",
                UPLOAD_FAILED_METRIC,
                Tags.of("reason", reason.name()));
    }

    @Override
    public void materialDeleted(UUID materialId) {
        Objects.requireNonNull(materialId);
        publishAfterCommitOrNow("material_deleted", () -> {
            publishSafely("material_deleted_log", () -> LOG.atInfo()
                    .addKeyValue("event", "material_deleted")
                    .addKeyValue("domain", DOMAIN)
                    .addKeyValue("operation", "delete")
                    .addKeyValue("materialId", materialId)
                    .addKeyValue("materialStatus", DELETED)
                    .log("Material deleted"));
            incrementSafely("material_deleted_metric", MATERIAL_DELETED_METRIC, Tags.empty());
            incrementSafely(
                    "material_deleted_status_metric",
                    STATUS_TRANSITIONS_METRIC,
                    Tags.of("scope", MATERIAL, "status", DELETED));
        });
    }

    private void publishAfterCommitOrNow(String telemetryOperation, Runnable publication) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            publishSafely(telemetryOperation, publication);
            return;
        }

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSafely(telemetryOperation, publication);
                }
            });
        } catch (RuntimeException registrationFailure) {
            warnSafely(telemetryOperation);
        }
    }

    private void incrementSafely(String telemetryOperation, String metricName, Tags tags) {
        publishSafely(telemetryOperation, () -> meterRegistry.counter(metricName, tags).increment());
    }

    private static void publishSafely(String telemetryOperation, Runnable publication) {
        try {
            publication.run();
        } catch (RuntimeException telemetryFailure) {
            warnSafely(telemetryOperation);
        }
    }

    private static void warnSafely(String telemetryOperation) {
        try {
            LOG.atWarn()
                    .addKeyValue("event", "material_lifecycle_telemetry_failed")
                    .addKeyValue("domain", DOMAIN)
                    .addKeyValue("operation", telemetryOperation)
                    .addKeyValue("errorCode", TELEMETRY_ERROR_CODE)
                    .log("Material lifecycle telemetry could not be published");
        } catch (RuntimeException ignored) {
            // Telemetry must never change the material operation's result.
        }
    }
}
