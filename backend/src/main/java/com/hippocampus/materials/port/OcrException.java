package com.hippocampus.materials.port;

import java.util.Objects;

public final class OcrException extends RuntimeException {
    private final Kind kind;

    public OcrException(Kind kind) {
        super(message(kind));
        this.kind = Objects.requireNonNull(kind);
    }

    public Kind kind() {
        return kind;
    }

    private static String message(Kind kind) {
        return switch (kind) {
            case ENGINE_UNAVAILABLE -> "OCR engine is unavailable";
            case TIMEOUT -> "OCR operation timed out";
            case NON_ZERO_EXIT -> "OCR engine rejected the input";
            case MALFORMED_OUTPUT -> "OCR engine returned malformed output";
            case INPUT_LIMIT_EXCEEDED -> "OCR input resource limit exceeded";
            case OUTPUT_LIMIT_EXCEEDED -> "OCR output resource limit exceeded";
            case PROCESS_IO_FAILED -> "OCR process I/O failed";
            case TERMINATION_FAILED -> "OCR process could not be terminated";
        };
    }

    public enum Kind {
        ENGINE_UNAVAILABLE,
        TIMEOUT,
        NON_ZERO_EXIT,
        MALFORMED_OUTPUT,
        INPUT_LIMIT_EXCEEDED,
        OUTPUT_LIMIT_EXCEEDED,
        PROCESS_IO_FAILED,
        TERMINATION_FAILED
    }
}
