package com.hippocampus.materials.port;

public final class PdfVisualExtractionException extends RuntimeException {
    private final Kind kind;

    public PdfVisualExtractionException(Kind kind) {
        super(message(kind));
        this.kind = kind;
    }

    public PdfVisualExtractionException(Kind kind, Throwable cause) {
        super(message(kind), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private static String message(Kind kind) {
        return switch (kind) {
            case SOURCE_NOT_AVAILABLE -> "PDF visual source is not available";
            case DOWNLOAD_FAILED -> "PDF visual source download failed";
            case CONTENT_TYPE_MISMATCH -> "Staged visual source is not a PDF";
            case MALFORMED_PDF -> "PDF visual source is malformed";
            case PASSWORD_PROTECTED -> "Password-protected PDF visuals cannot be extracted";
            case PAGE_LIMIT_EXCEEDED -> "PDF visual page limit exceeded";
            case RESOURCE_LIMIT_EXCEEDED -> "PDF visual extraction resource limit exceeded";
            case EXTRACTION_FAILED -> "PDF visual extraction failed";
            case STORAGE_FAILED -> "Extracted visual storage failed";
            case OUTPUT_REJECTED -> "PDF visual output was rejected";
            case TEMPORARY_STORAGE_FAILED -> "Temporary PDF visual storage failed";
            case TEMPORARY_CLEANUP_FAILED -> "Temporary PDF visual cleanup failed";
        };
    }

    public enum Kind {
        SOURCE_NOT_AVAILABLE,
        DOWNLOAD_FAILED,
        CONTENT_TYPE_MISMATCH,
        MALFORMED_PDF,
        PASSWORD_PROTECTED,
        PAGE_LIMIT_EXCEEDED,
        RESOURCE_LIMIT_EXCEEDED,
        EXTRACTION_FAILED,
        STORAGE_FAILED,
        OUTPUT_REJECTED,
        TEMPORARY_STORAGE_FAILED,
        TEMPORARY_CLEANUP_FAILED
    }
}
