package com.hippocampus.materials.infrastructure.web;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

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
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadFailureReason;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadRejectionReason;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class MaterialUploadExceptionHandler {

    private final Optional<MaterialLifecycleTelemetry> telemetry;

    public MaterialUploadExceptionHandler(Optional<MaterialLifecycleTelemetry> telemetry) {
        this.telemetry = telemetry;
    }

    @ExceptionHandler(MaterialUploadException.class)
    ResponseEntity<ProblemDetail> handleMaterialUpload(MaterialUploadException exception, HttpServletRequest request) {
        return switch (exception.kind()) {
            case FILE_REQUIRED -> rejected(
                    UploadRejectionReason.UPLOAD_FILE_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "One upload file is required.",
                    request);
            case SINGLE_FILE_REQUIRED -> rejected(
                    UploadRejectionReason.UPLOAD_SINGLE_FILE_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "Exactly one upload file is required.",
                    request);
            case EMPTY -> rejected(
                    UploadRejectionReason.UPLOAD_EMPTY,
                    HttpStatus.BAD_REQUEST,
                    "The upload file must not be empty.",
                    request);
            case TOO_LARGE -> rejected(
                    UploadRejectionReason.UPLOAD_TOO_LARGE,
                    HttpStatus.CONTENT_TOO_LARGE,
                    "The upload file exceeds the configured limit.",
                    request);
            case TYPE_UNSUPPORTED -> rejected(
                    UploadRejectionReason.UPLOAD_TYPE_UNSUPPORTED,
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The upload content type is not supported.",
                    request);
            case TYPE_MISMATCH -> rejected(
                    UploadRejectionReason.UPLOAD_TYPE_MISMATCH,
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The upload content type could not be verified.",
                    request);
            case CONTENT_INVALID -> rejected(
                    UploadRejectionReason.UPLOAD_CONTENT_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "The upload content could not be verified.",
                    request);
            case STORAGE_UNAVAILABLE -> failed(
                    UploadFailureReason.UPLOAD_STORAGE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The upload could not be stored.",
                    request);
            case PERSISTENCE_FAILED -> failed(
                    UploadFailureReason.UPLOAD_PERSISTENCE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The upload could not be completed.",
                    request);
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleMaximumUploadSize(HttpServletRequest request) {
        return rejected(
                UploadRejectionReason.UPLOAD_TOO_LARGE,
                HttpStatus.CONTENT_TOO_LARGE,
                "The upload file exceeds the configured limit.",
                request);
    }

    private ResponseEntity<ProblemDetail> rejected(
            UploadRejectionReason reason,
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        try {
            telemetry.ifPresent(value -> value.uploadRejected(reason));
        } catch (RuntimeException ignored) {
            // Preserve the sanitized response when telemetry is unavailable.
        }
        return problem(status, reason.name(), message, request);
    }

    private ResponseEntity<ProblemDetail> failed(
            UploadFailureReason reason,
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        try {
            telemetry.ifPresent(value -> value.uploadFailed(reason));
        } catch (RuntimeException ignored) {
            // Preserve the sanitized response when telemetry is unavailable.
        }
        return problem(status, reason.name(), message, request);
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
