package com.hippocampus.materials.application;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;

import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfStructureInspectionException;
import com.hippocampus.materials.port.PdfTableExtractionException;
import com.hippocampus.materials.port.PdfVisualExtractionException;
import com.hippocampus.materials.port.MaterialSourceValidationException;

public final class ProcessingFailureClassifier {
    public ProcessingFailure classify(RuntimeException failure) {
        if (failure instanceof TransientDataAccessException
                || failure instanceof DataAccessResourceFailureException) return transientFailure("DB_UNAVAILABLE");
        if (failure instanceof BinaryObjectStoreException) return transientFailure("STORAGE_UNAVAILABLE");
        if (failure instanceof MaterialSourceValidationException) return fatalFailure("SOURCE_VALIDATION_FAILED");
        if (failure instanceof OcrException ocr) return switch (ocr.kind()) {
            case ENGINE_UNAVAILABLE, TIMEOUT, PROCESS_IO_FAILED, TERMINATION_FAILED -> transientFailure("OCR_UNAVAILABLE");
            case NON_ZERO_EXIT, MALFORMED_OUTPUT, INPUT_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED -> fatalFailure("OCR_FAILED");
        };
        if (failure instanceof PdfExtractionException pdf) return classifyPdf(pdf);
        if (failure instanceof PdfStructureInspectionException structure) return switch (structure.kind()) {
            case DOWNLOAD_FAILED, TEMPORARY_STORAGE_FAILED, TEMPORARY_CLEANUP_FAILED -> transientFailure("STORAGE_UNAVAILABLE");
            case SOURCE_NOT_AVAILABLE, CONTENT_TYPE_MISMATCH, PASSWORD_PROTECTED, MALFORMED_PDF,
                    RESOURCE_LIMIT_EXCEEDED, INSPECTION_FAILED, OUTPUT_REJECTED -> fatalFailure("STRUCTURE_DETECTION_FAILED");
        };
        if (failure instanceof PdfVisualExtractionException visual) return switch (visual.kind()) {
            case DOWNLOAD_FAILED, STORAGE_FAILED, TEMPORARY_STORAGE_FAILED, TEMPORARY_CLEANUP_FAILED -> transientFailure("STORAGE_UNAVAILABLE");
            case SOURCE_NOT_AVAILABLE, CONTENT_TYPE_MISMATCH, MALFORMED_PDF, PASSWORD_PROTECTED,
                    PAGE_LIMIT_EXCEEDED, RESOURCE_LIMIT_EXCEEDED, EXTRACTION_FAILED, OUTPUT_REJECTED -> fatalFailure("VISUAL_EXTRACTION_FAILED");
        };
        if (failure instanceof PdfTableExtractionException table) return switch (table.kind()) {
            case DOWNLOAD_FAILED, TEMPORARY_STORAGE_FAILED, TEMPORARY_CLEANUP_FAILED -> transientFailure("STORAGE_UNAVAILABLE");
            case SOURCE_NOT_AVAILABLE, CONTENT_TYPE_MISMATCH, PASSWORD_PROTECTED, MALFORMED_PDF,
                    PAGE_LIMIT_EXCEEDED, RESOURCE_LIMIT_EXCEEDED, OUTPUT_REJECTED -> fatalFailure("TABLE_EXTRACTION_FAILED");
        };
        return fatalFailure("PROCESSING_INTERNAL_ERROR");
    }

    private ProcessingFailure classifyPdf(PdfExtractionException failure) {
        if (failure.kind() == PdfExtractionException.Kind.OCR_FAILED
                && failure.getCause() instanceof OcrException ocr) {
            return classify(ocr);
        }
        return switch (failure.kind()) {
            case DOWNLOAD_FAILED, TEMPORARY_STORAGE_FAILED, TEMPORARY_CLEANUP_FAILED -> transientFailure("STORAGE_UNAVAILABLE");
            case SOURCE_NOT_AVAILABLE, SOURCE_NOT_EXTRACTABLE, CONTENT_TYPE_MISMATCH, MALFORMED_PDF,
                    PASSWORD_PROTECTED, PAGE_LIMIT_EXCEEDED, RESOURCE_LIMIT_EXCEEDED, EXTRACTION_FAILED,
                    OCR_FAILED, OUTPUT_REJECTED -> fatalFailure("EXTRACTION_FAILED");
        };
    }

    private static ProcessingFailure transientFailure(String code) {
        return new ProcessingFailure(ProcessingFailure.Kind.TRANSIENT, code);
    }

    private static ProcessingFailure fatalFailure(String code) {
        return new ProcessingFailure(ProcessingFailure.Kind.FATAL, code);
    }
}
