package com.hippocampus.shared.infrastructure.web;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    static final String MDC_KEY = "correlationId";

    private static final Logger LOG = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        long startedAt = System.nanoTime();

        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                LOG.atInfo()
                        .addKeyValue("event", "http_request_completed")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("requestPath", request.getRequestURI())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue(
                                "durationMs",
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                        .log("HTTP request completed");
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }

    public static String currentCorrelationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(REQUEST_ATTRIBUTE);
        if (correlationId instanceof String value) {
            return value;
        }

        String generated = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, generated);
        return generated;
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        String supplied = request.getHeader(HEADER_NAME);
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String candidate = supplied.trim();
        try {
            UUID parsed = UUID.fromString(candidate);
            String canonical = parsed.toString();
            return canonical.equalsIgnoreCase(candidate) ? canonical : UUID.randomUUID().toString();
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID().toString();
        }
    }
}
