package com.hippocampus.materials.port;

public final class PdfStructureInspectionException extends RuntimeException {
    private final Kind kind;

    public PdfStructureInspectionException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public PdfStructureInspectionException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        SOURCE_NOT_AVAILABLE,
        DOWNLOAD_FAILED,
        CONTENT_TYPE_MISMATCH,
        PASSWORD_PROTECTED,
        MALFORMED_PDF,
        RESOURCE_LIMIT_EXCEEDED,
        TEMPORARY_STORAGE_FAILED,
        TEMPORARY_CLEANUP_FAILED,
        INSPECTION_FAILED,
        OUTPUT_REJECTED
    }
}
