package com.hippocampus.learning.infrastructure.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public final class ActivityResponseBodySizeFilter extends OncePerRequestFilter {

    public static final int MAX_REQUEST_BODY_BYTES = 16 * 1024;

    private static final Pattern ACTIVITY_RESPONSE_PATH = Pattern.compile(
            "/api/study-missions/[^/]+/activities/[^/]+/responses");
    private static final String ERROR_CODE = "REQUEST_BODY_TOO_LARGE";
    private static final String ERROR_MESSAGE = "Request body is too large.";

    private final ObjectMapper objectMapper;

    public ActivityResponseBodySizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return !ACTIVITY_RESPONSE_PATH.matcher(requestPath).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BODY_BYTES) {
            writePayloadTooLarge(request, response);
            return;
        }

        byte[] body = readBounded(request.getInputStream());
        if (body == null) {
            writePayloadTooLarge(request, response);
            return;
        }

        filterChain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private static byte[] readBounded(ServletInputStream input) throws IOException {
        byte[] body = new byte[MAX_REQUEST_BODY_BYTES + 1];
        int offset = 0;
        while (offset < body.length) {
            int read = input.read(body, offset, body.length - offset);
            if (read < 0) {
                return Arrays.copyOf(body, offset);
            }
            if (read == 0) {
                int next = input.read();
                if (next < 0) {
                    return Arrays.copyOf(body, offset);
                }
                body[offset++] = (byte) next;
            } else {
                offset += read;
            }
        }
        return null;
    }

    private void writePayloadTooLarge(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String correlationId = CorrelationIdFilter.currentCorrelationId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, ERROR_MESSAGE);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ERROR_CODE);
        problem.setProperty("message", ERROR_MESSAGE);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("details", Map.of());

        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new BufferedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class BufferedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private BufferedBodyServletInputStream(byte[] body) {
            input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                throw new IllegalArgumentException("ReadListener is required.");
            }
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
