package com.hippocampus.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.DomainConflictException;
import com.hippocampus.shared.domain.error.ErrorCode;
import com.jayway.jsonpath.JsonPath;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@WebMvcTest(controllers = ApiErrorContractTests.ErrorContractController.class)
@Import({
        CorrelationIdFilter.class,
        ApiExceptionHandler.class,
        ApiErrorContractTests.ErrorContractController.class
})
class ApiErrorContractTests {

    private static final String CORRELATION_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
    private static final String UPPERCASE_CORRELATION_ID = CORRELATION_ID.toUpperCase();
    private static final String REJECTED_VALUE = "PRIVATE_REJECTED_VALUE_42018";
    private static final String MALFORMED_CONTENT = "PRIVATE_MALFORMED_CONTENT_72914";
    private static final String INTERNAL_MARKER = "PRIVATE_INTERNAL_DETAIL_82531";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationFailureReturnsStableSanitizedProblemDetail() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .header(CorrelationIdFilter.HEADER_NAME, UPPERCASE_CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + REJECTED_VALUE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.instance").value("/test/errors/validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.details.fieldErrors[0].field").value("value"))
                .andExpect(jsonPath("$.details.fieldErrors[0].message").value("is invalid"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(REJECTED_VALUE))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("rejectedValue"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("FieldError"))));
    }

    @Test
    void applicationNotFoundReturnsStableProblemDetail() throws Exception {
        mockMvc.perform(get("/test/errors/not-found")
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("The requested test resource was not found."))
                .andExpect(jsonPath("$.code").value("TEST_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested test resource was not found."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void domainConflictReturnsStableProblemDetail() throws Exception {
        mockMvc.perform(get("/test/errors/conflict")
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value("The requested test state conflicts with current state."))
                .andExpect(jsonPath("$.code").value("TEST_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("The requested test state conflicts with current state."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        var result = mockMvc.perform(get("/test/errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(INTERNAL_MARKER))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException"))))
                .andReturn();

        assertGeneratedCorrelationId(result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME),
                result.getResponse().getContentAsString());
    }

    @Test
    void malformedJsonReturnsSanitizedFrameworkProblemDetail() throws Exception {
        var result = mockMvc.perform(post("/test/errors/validation")
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + MALFORMED_CONTENT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The request body is malformed."))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request body is malformed."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.details").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(
                        MALFORMED_CONTENT,
                        "JsonParseException",
                        "Jackson",
                        "Unexpected end-of-input",
                        "HttpMessageNotReadableException");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "not-a-valid-uuid")
    void missingBlankOrInvalidCorrelationIdIsReplaced(String suppliedCorrelationId) throws Exception {
        var request = get("/test/errors/not-found");
        if (suppliedCorrelationId != null) {
            request.header(CorrelationIdFilter.HEADER_NAME, suppliedCorrelationId);
        }

        var result = mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertGeneratedCorrelationId(result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME),
                result.getResponse().getContentAsString());
        if (suppliedCorrelationId != null) {
            assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME))
                    .isNotEqualTo(suppliedCorrelationId);
        }
    }

    private static void assertGeneratedCorrelationId(String responseHeader, String responseBody) {
        assertThat(responseHeader).isNotNull();
        assertThat(UUID.fromString(responseHeader).toString()).isEqualTo(responseHeader);
        assertThat((String) JsonPath.read(responseBody, "$.correlationId")).isEqualTo(responseHeader);
    }

    @RestController
    @RequestMapping("/test/errors")
    static final class ErrorContractController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ApplicationNotFoundException(
                    new ErrorCode("TEST_RESOURCE_NOT_FOUND"),
                    "The requested test resource was not found.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new DomainConflictException(
                    new ErrorCode("TEST_STATE_CONFLICT"),
                    "The requested test state conflicts with current state.");
        }

        @GetMapping("/internal")
        void internal() {
            throw new IllegalStateException(INTERNAL_MARKER);
        }
    }

    private record ValidationRequest(@Size(max = 3) String value) {
    }
}
