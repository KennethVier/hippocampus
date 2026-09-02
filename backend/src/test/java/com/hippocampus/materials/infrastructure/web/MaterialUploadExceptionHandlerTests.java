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

class MaterialUploadExceptionHandlerTests {

    @Test
    void resolvesContainerMaximumUploadExceptionWithoutAControllerHandler() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
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
    }

    @Test
    void resolvesInvalidContentWithoutParserOrStorageDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
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
    }
}
