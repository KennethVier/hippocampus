package com.hippocampus.materials.port;

public final class MaterialSourceValidationException extends RuntimeException {
    private final Kind kind;

    public MaterialSourceValidationException(Kind kind) {
        super(message(kind));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private static String message(Kind kind) {
        return switch (kind) {
            case SOURCE_NOT_AVAILABLE -> "Material source is not available";
            case SOURCE_NOT_PROCESSABLE -> "Material source is not processable";
        };
    }

    public enum Kind {
        SOURCE_NOT_AVAILABLE,
        SOURCE_NOT_PROCESSABLE
    }
}
