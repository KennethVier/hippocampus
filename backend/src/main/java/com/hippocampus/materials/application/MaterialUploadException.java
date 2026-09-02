package com.hippocampus.materials.application;

public final class MaterialUploadException extends RuntimeException {

    private final Kind kind;

    public MaterialUploadException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public MaterialUploadException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        FILE_REQUIRED,
        SINGLE_FILE_REQUIRED,
        EMPTY,
        TOO_LARGE,
        TYPE_UNSUPPORTED,
        TYPE_MISMATCH,
        CONTENT_INVALID,
        STORAGE_UNAVAILABLE,
        PERSISTENCE_FAILED
    }
}
