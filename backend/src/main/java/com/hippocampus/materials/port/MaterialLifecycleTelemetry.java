package com.hippocampus.materials.port;

import java.util.UUID;

/**
 * Publishes privacy-safe operational signals for authoritative material lifecycle
 * outcomes. Implementations must not let telemetry failures alter material
 * operations.
 */
public interface MaterialLifecycleTelemetry {

    void uploadAccepted(UUID materialId, UUID materialVersionId);

    void uploadRejected(UploadRejectionReason reason);

    void uploadFailed(UploadFailureReason reason);

    void materialDeleted(UUID materialId);

    enum UploadRejectionReason {
        UPLOAD_FILE_REQUIRED,
        UPLOAD_SINGLE_FILE_REQUIRED,
        UPLOAD_EMPTY,
        UPLOAD_TOO_LARGE,
        UPLOAD_TYPE_UNSUPPORTED,
        UPLOAD_TYPE_MISMATCH,
        UPLOAD_CONTENT_INVALID
    }

    enum UploadFailureReason {
        UPLOAD_STORAGE_UNAVAILABLE,
        UPLOAD_PERSISTENCE_FAILED
    }
}
