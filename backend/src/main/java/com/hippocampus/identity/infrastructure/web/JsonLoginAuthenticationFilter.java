package com.hippocampus.identity.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;
import com.hippocampus.identity.infrastructure.security.LoginRateLimiter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

public final class JsonLoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final ObjectMapper objectMapper;
    private final LoginRateLimiter limiter;
    private final SecurityProblemWriter problems;

    public JsonLoginAuthenticationFilter(AuthenticationManager manager, ObjectMapper objectMapper,
            LoginRateLimiter limiter, SecurityProblemWriter problems) {
        super(request -> "POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath()), manager);
        this.objectMapper = objectMapper;
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException {
        if (!limiter.allow(request.getRemoteAddr())) {
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "AUTHENTICATION_RATE_LIMITED",
                    "Too many authentication attempts. Try again later.");
            return null;
        }
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(request.getContentType());
        } catch (RuntimeException exception) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed.");
            return null;
        }
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed.");
            return null;
        }
        LoginRequest body;
        try { body = objectMapper.readValue(request.getInputStream(), LoginRequest.class); }
        catch (Exception exception) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed.");
            return null;
        }
        if (body.email == null || body.email.isBlank() || body.password == null || body.password.isBlank()
                || body.password.getBytes(StandardCharsets.UTF_8).length > 72) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
            return null;
        }
        return getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email, body.password));
    }

    private record LoginRequest(String email, String password) {}
}
