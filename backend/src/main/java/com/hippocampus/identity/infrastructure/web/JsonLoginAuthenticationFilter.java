package com.hippocampus.identity.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import com.hippocampus.identity.infrastructure.security.LoginRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

public final class JsonLoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String MALFORMED_REQUEST_CODE = "MALFORMED_REQUEST";
    private static final String MALFORMED_REQUEST_MESSAGE = "The request body is malformed.";
    private static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    private static final String VALIDATION_FAILED_MESSAGE = "Request validation failed.";

    private final ObjectMapper objectMapper;
    private final LoginRateLimiter limiter;
    private final SecurityProblemWriter problems;

    public JsonLoginAuthenticationFilter(
            AuthenticationManager manager,
            ObjectMapper objectMapper,
            LoginRateLimiter limiter,
            SecurityProblemWriter problems) {
        super(
                request -> "POST".equals(request.getMethod())
                        && "/api/auth/login".equals(request.getServletPath()),
                manager);
        this.objectMapper = objectMapper;
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response) throws AuthenticationException, IOException {
        if (!limiter.allow(request.getRemoteAddr())) {
            problems.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AUTHENTICATION_RATE_LIMITED",
                    "Too many authentication attempts. Try again later.");
            return null;
        }

        MediaType contentType = parseContentType(request, response);
        if (contentType == null) {
            return null;
        }
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            writeMalformedRequest(request, response);
            return null;
        }

        LoginRequest body = readBody(request, response);
        if (body == null) {
            return null;
        }
        if (!isValid(body)) {
            problems.write(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    VALIDATION_FAILED_CODE,
                    VALIDATION_FAILED_MESSAGE);
            return null;
        }

        return getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
    }

    private MediaType parseContentType(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return MediaType.parseMediaType(request.getContentType());
        } catch (RuntimeException exception) {
            writeMalformedRequest(request, response);
            return null;
        }
    }

    private LoginRequest readBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (Exception exception) {
            writeMalformedRequest(request, response);
            return null;
        }
    }

    private static boolean isValid(LoginRequest body) {
        return body.email() != null
                && !body.email().isBlank()
                && body.password() != null
                && !body.password().isBlank()
                && body.password().getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private void writeMalformedRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problems.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                MALFORMED_REQUEST_CODE,
                MALFORMED_REQUEST_MESSAGE);
    }

    private record LoginRequest(String email, String password) {
    }
}
