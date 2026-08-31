package com.hippocampus.materials.port;

public class BinaryObjectStoreException extends RuntimeException {

    public BinaryObjectStoreException(String message) {
        super(message);
    }

    public BinaryObjectStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
