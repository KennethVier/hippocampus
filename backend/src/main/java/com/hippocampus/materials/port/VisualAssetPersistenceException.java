package com.hippocampus.materials.port;

public final class VisualAssetPersistenceException extends RuntimeException {
    public VisualAssetPersistenceException(String message) {
        super(message);
    }

    public VisualAssetPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
