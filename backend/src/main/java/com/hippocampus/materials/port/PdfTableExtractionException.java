package com.hippocampus.materials.port;

public final class PdfTableExtractionException extends RuntimeException {
    public enum Kind {
        SOURCE_NOT_AVAILABLE,
        DOWNLOAD_FAILED,
        CONTENT_TYPE_MISMATCH,
        PASSWORD_PROTECTED,
        MALFORMED_PDF,
        PAGE_LIMIT_EXCEEDED,
        RESOURCE_LIMIT_EXCEEDED,
        OUTPUT_REJECTED,
        TEMPORARY_STORAGE_FAILED,
        TEMPORARY_CLEANUP_FAILED
    }

    private final Kind kind;

    public PdfTableExtractionException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public PdfTableExtractionException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
