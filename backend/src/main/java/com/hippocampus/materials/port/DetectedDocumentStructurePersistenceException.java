package com.hippocampus.materials.port;

public final class DetectedDocumentStructurePersistenceException extends RuntimeException {
    public DetectedDocumentStructurePersistenceException(String message) {
        super(message);
    }

    public DetectedDocumentStructurePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
