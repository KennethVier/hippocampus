package com.hippocampus.materials.infrastructure.web;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.hippocampus.materials.application.MaterialUploadException;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class MaterialUploadExceptionHandler {

    @ExceptionHandler(MaterialUploadException.class)
    ResponseEntity<ProblemDetail> handleMaterialUpload(MaterialUploadException exception, HttpServletRequest request) {
        return switch (exception.kind()) {
            case FILE_REQUIRED -> problem(HttpStatus.BAD_REQUEST, "UPLOAD_FILE_REQUIRED", "One upload file is required.", request);
            case SINGLE_FILE_REQUIRED -> problem(HttpStatus.BAD_REQUEST, "UPLOAD_SINGLE_FILE_REQUIRED", "Exactly one upload file is required.", request);
            case EMPTY -> problem(HttpStatus.BAD_REQUEST, "UPLOAD_EMPTY", "The upload file must not be empty.", request);
            case TOO_LARGE -> problem(HttpStatus.CONTENT_TOO_LARGE, "UPLOAD_TOO_LARGE", "The upload file exceeds the configured limit.", request);
            case TYPE_UNSUPPORTED -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UPLOAD_TYPE_UNSUPPORTED", "The declared upload type is not supported.", request);
            case STORAGE_UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "UPLOAD_STORAGE_UNAVAILABLE", "The upload could not be stored.", request);
            case PERSISTENCE_FAILED -> problem(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_PERSISTENCE_FAILED", "The upload could not be completed.", request);
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleMaximumUploadSize(HttpServletRequest request) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "UPLOAD_TOO_LARGE", "The upload file exceeds the configured limit.", request);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.currentCorrelationId(request);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("about:blank"));
        detail.setTitle(status.getReasonPhrase());
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        detail.setProperty("message", message);
        detail.setProperty("correlationId", correlationId);
        detail.setProperty("details", Map.of());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
        return new ResponseEntity<>(detail, headers, status);
    }
}
