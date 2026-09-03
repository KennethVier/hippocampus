package com.hippocampus.materials.application;

public final class PdfExtractionException extends RuntimeException {
    private final Kind kind;

    public PdfExtractionException(Kind kind) {
        super(message(kind));
        this.kind = kind;
    }

    public PdfExtractionException(Kind kind, Throwable cause) {
        super(message(kind), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private static String message(Kind kind) {
        return switch (kind) {
            case SOURCE_NOT_AVAILABLE -> "PDF extraction source is not available";
            case SOURCE_NOT_EXTRACTABLE -> "Material version is not an extractable PDF";
            case DOWNLOAD_FAILED -> "PDF source download failed";
            case CONTENT_TYPE_MISMATCH -> "Staged extraction source is not a PDF";
            case MALFORMED_PDF -> "PDF source is malformed";
            case PASSWORD_PROTECTED -> "Password-protected PDF cannot be extracted";
            case PAGE_LIMIT_EXCEEDED -> "PDF page limit exceeded";
            case RESOURCE_LIMIT_EXCEEDED -> "PDF extraction resource limit exceeded";
            case EXTRACTION_FAILED -> "PDF native text extraction failed";
            case OUTPUT_REJECTED -> "PDF extraction output was rejected";
            case TEMPORARY_STORAGE_FAILED -> "Temporary PDF storage failed";
            case TEMPORARY_CLEANUP_FAILED -> "Temporary PDF cleanup failed";
        };
    }

    public enum Kind {
        SOURCE_NOT_AVAILABLE,
        SOURCE_NOT_EXTRACTABLE,
        DOWNLOAD_FAILED,
        CONTENT_TYPE_MISMATCH,
        MALFORMED_PDF,
        PASSWORD_PROTECTED,
        PAGE_LIMIT_EXCEEDED,
        RESOURCE_LIMIT_EXCEEDED,
        EXTRACTION_FAILED,
        OUTPUT_REJECTED,
        TEMPORARY_STORAGE_FAILED,
        TEMPORARY_CLEANUP_FAILED
    }
}
