package com.hippocampus.shared.infrastructure.web;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.DomainConflictException;
import com.hippocampus.shared.domain.error.DomainException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
final class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Map<String, Object> NO_DETAILS = Map.of();

    @ExceptionHandler(DomainConflictException.class)
    ResponseEntity<Object> handleDomainConflict(
            DomainConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                exception.errorCode().value(),
                exception.clientMessage(),
                NO_DETAILS,
                request);
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    ResponseEntity<Object> handleApplicationNotFound(
            ApplicationNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                exception.errorCode().value(),
                exception.clientMessage(),
                NO_DETAILS,
                request);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomainException(DomainException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.errorCode().value(),
                exception.clientMessage(),
                NO_DETAILS,
                request);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<Object> handleApplicationException(
            ApplicationException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.errorCode().value(),
                exception.clientMessage(),
                NO_DETAILS,
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        var correlationId = CorrelationIdFilter.currentCorrelationId(request);
        LOG.atError()
                .addKeyValue("event", "unhandled_exception")
                .addKeyValue("errorCode", "INTERNAL_ERROR")
                .setCause(exception)
                .log("Unhandled exception");
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                NO_DETAILS,
                correlationId,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(field -> new FieldValidationError(field, "is invalid"))
                .toList();

        return problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed.",
                Map.of("fieldErrors", fieldErrors),
                servletRequest(request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "The request body is malformed.",
                NO_DETAILS,
                servletRequest(request));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var servletRequest = servletRequest(request);
        if (status.is5xxServerError()) {
            var correlationId = CorrelationIdFilter.currentCorrelationId(servletRequest);
            LOG.atError()
                    .addKeyValue("event", "unhandled_framework_exception")
                    .addKeyValue("errorCode", "INTERNAL_ERROR")
                    .setCause(exception)
                    .log("Unhandled framework exception");
            return problem(
                    status,
                    "INTERNAL_ERROR",
                    "An unexpected error occurred.",
                    NO_DETAILS,
                    correlationId,
                    servletRequest);
        }

        return problem(
                status,
                "REQUEST_REJECTED",
                "The request could not be completed.",
                NO_DETAILS,
                servletRequest);
    }

    private static ResponseEntity<Object> problem(
            HttpStatusCode status,
            String code,
            String message,
            Map<String, ?> details,
            HttpServletRequest request) {
        return problem(
                status,
                code,
                message,
                details,
                CorrelationIdFilter.currentCorrelationId(request),
                request);
    }

    private static ResponseEntity<Object> problem(
            HttpStatusCode status,
            String code,
            String message,
            Map<String, ?> details,
            String correlationId,
        HttpServletRequest request) {
        var problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        problemDetail.setType(URI.create("about:blank"));
        var resolvedStatus = HttpStatus.resolve(status.value());
        problemDetail.setTitle(resolvedStatus == null ? "Request Failed" : resolvedStatus.getReasonPhrase());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("message", message);
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("details", details);

        var responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        responseHeaders.set(CorrelationIdFilter.HEADER_NAME, correlationId);
        return new ResponseEntity<>(problemDetail, responseHeaders, status);
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private record FieldValidationError(String field, String message) {
    }
}
