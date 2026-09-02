package com.hippocampus.materials.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import com.hippocampus.materials.application.MaterialUploadException;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadFailureReason;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadRejectionReason;

class MaterialUploadExceptionHandlerTests {

    @Test
    void resolvesContainerMaximumUploadExceptionWithoutAControllerHandler() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MaterialLifecycleTelemetry.class, () -> telemetry);
            context.registerBean(MaterialUploadExceptionHandler.class);
            context.refresh();
            ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
            resolver.setApplicationContext(context);
            resolver.setMessageConverters(java.util.List.of(new JacksonJsonHttpMessageConverter()));
            resolver.afterPropertiesSet();

            assertThat(resolver.resolveException(
                    request, response, null, new MaxUploadSizeExceededException(8))).isNotNull();
        }

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UPLOAD_TOO_LARGE\"", "\"correlationId\"")
                .doesNotContain("/var/", "storage key", "provider");
        assertThat(telemetry.rejected).containsExactly(UploadRejectionReason.UPLOAD_TOO_LARGE);
        assertThat(telemetry.failed).isEmpty();
    }

    @Test
    void resolvesInvalidContentWithoutParserOrStorageDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MaterialLifecycleTelemetry.class, () -> telemetry);
            context.registerBean(MaterialUploadExceptionHandler.class);
            context.refresh();
            ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
            resolver.setApplicationContext(context);
            resolver.setMessageConverters(java.util.List.of(new JacksonJsonHttpMessageConverter()));
            resolver.afterPropertiesSet();

            assertThat(resolver.resolveException(
                    request,
                    response,
                    null,
                    new MaterialUploadException(MaterialUploadException.Kind.CONTENT_INVALID,
                            new IllegalStateException("Tika parser internals /var/storage/key")))).isNotNull();
        }

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UPLOAD_CONTENT_INVALID\"", "\"correlationId\"")
                .doesNotContain("Tika", "/var/", "storage/key", "parser internals");
        assertThat(telemetry.rejected).containsExactly(UploadRejectionReason.UPLOAD_CONTENT_INVALID);
        assertThat(telemetry.failed).isEmpty();
    }

    @Test
    void classifiesControllerInputRejectionExactlyOnce() throws Exception {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        MockHttpServletResponse response = resolve(
                new MaterialUploadException(MaterialUploadException.Kind.FILE_REQUIRED), telemetry);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":\"UPLOAD_FILE_REQUIRED\"");
        assertThat(telemetry.rejected).containsExactly(UploadRejectionReason.UPLOAD_FILE_REQUIRED);
        assertThat(telemetry.failed).isEmpty();
    }

    @Test
    void classifiesOperationalFailuresSeparatelyWithoutRawMessages() throws Exception {
        for (MaterialUploadException.Kind kind : new MaterialUploadException.Kind[] {
                MaterialUploadException.Kind.STORAGE_UNAVAILABLE,
                MaterialUploadException.Kind.PERSISTENCE_FAILED}) {
            RecordingTelemetry telemetry = new RecordingTelemetry();
            MockHttpServletResponse response = resolve(
                    new MaterialUploadException(
                            kind,
                            new IllegalStateException("PRIVATE_EXCEPTION_SENTINEL /private/storage/path")),
                    telemetry);

            assertThat(telemetry.rejected).isEmpty();
            assertThat(telemetry.failed).hasSize(1);
            assertThat(response.getContentAsString())
                    .doesNotContain("PRIVATE_EXCEPTION_SENTINEL", "/private/storage/path");
        }
    }

    @Test
    void telemetryFailureDoesNotChangeSanitizedResponse() throws Exception {
        MaterialLifecycleTelemetry telemetry = new RecordingTelemetry() {
            @Override public void uploadRejected(UploadRejectionReason reason) {
                throw new IllegalStateException("PRIVATE_EXCEPTION_SENTINEL");
            }
        };

        MockHttpServletResponse response = resolve(
                new MaterialUploadException(MaterialUploadException.Kind.EMPTY), telemetry);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UPLOAD_EMPTY\"")
                .doesNotContain("PRIVATE_EXCEPTION_SENTINEL");
    }

    private static MockHttpServletResponse resolve(
            Exception exception,
            MaterialLifecycleTelemetry telemetry) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MaterialLifecycleTelemetry.class, () -> telemetry);
            context.registerBean(MaterialUploadExceptionHandler.class);
            context.refresh();
            ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
            resolver.setApplicationContext(context);
            resolver.setMessageConverters(java.util.List.of(new JacksonJsonHttpMessageConverter()));
            resolver.afterPropertiesSet();
            assertThat(resolver.resolveException(request, response, null, exception)).isNotNull();
        }
        return response;
    }

    private static class RecordingTelemetry implements MaterialLifecycleTelemetry {
        final java.util.List<UploadRejectionReason> rejected = new java.util.ArrayList<>();
        final java.util.List<UploadFailureReason> failed = new java.util.ArrayList<>();

        @Override public void uploadAccepted(java.util.UUID materialId, java.util.UUID materialVersionId) {}
        @Override public void uploadRejected(UploadRejectionReason reason) { rejected.add(reason); }
        @Override public void uploadFailed(UploadFailureReason reason) { failed.add(reason); }
        @Override public void materialDeleted(java.util.UUID materialId) {}
    }
}
