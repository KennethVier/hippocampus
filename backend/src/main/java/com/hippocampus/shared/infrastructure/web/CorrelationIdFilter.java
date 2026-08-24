package com.hippocampus.shared.infrastructure.web;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
final class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Correlation-ID";
    private static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var correlationId = resolveCorrelationId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        filterChain.doFilter(request, response);
    }

    static String currentCorrelationId(HttpServletRequest request) {
        var correlationId = request.getAttribute(REQUEST_ATTRIBUTE);
        if (correlationId instanceof String value) {
            return value;
        }

        var generated = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, generated);
        return generated;
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        var supplied = request.getHeader(HEADER_NAME);
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }

        var candidate = supplied.trim();
        try {
            var parsed = UUID.fromString(candidate);
            var canonical = parsed.toString();
            return canonical.equalsIgnoreCase(candidate) ? canonical : UUID.randomUUID().toString();
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID().toString();
        }
    }
}
