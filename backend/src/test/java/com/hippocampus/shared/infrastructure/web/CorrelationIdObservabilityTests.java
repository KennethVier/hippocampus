package com.hippocampus.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = CorrelationIdObservabilityTests.ObservabilityTestController.class, properties = {
                "debug=false",
                "logging.level.root=INFO",
                "logging.level.org.springframework.web=INFO",
                "logging.structured.format.console=ecs",
                "logging.structured.ecs.service.environment=test"
        })
@Import({
        CorrelationIdFilter.class,
        ApiExceptionHandler.class,
        CorrelationIdObservabilityTests.ObservabilityTestController.class
})
class CorrelationIdObservabilityTests {

    private static final String FIRST_CORRELATION_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
    private static final String SECOND_CORRELATION_ID = "4e8a8f68-91ad-4c23-b0d8-63f77f93df51";
    private static final String AUTHORIZATION_SECRET = "AUTHORIZATION_SECRET_51792";
    private static final String COOKIE_SECRET = "COOKIE_SECRET_60418";
    private static final String QUERY_SECRET = "QUERY_SECRET_26374";
    private static final String BODY_SECRET = "BODY_SECRET_94831";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestCompletionIsStructuredAndUsesTheExistingCorrelationId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/observability/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, FIRST_CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, FIRST_CORRELATION_ID));

        var event = latestEvent(output, "http_request_completed");
        assertThat((String) JsonPath.read(event, "$.event")).isEqualTo("http_request_completed");
        assertThat((String) JsonPath.read(event, "$.correlationId")).isEqualTo(FIRST_CORRELATION_ID);
        assertThat((String) JsonPath.read(event, "$.method")).isEqualTo("GET");
        assertThat((String) JsonPath.read(event, "$.requestPath")).isEqualTo("/test/observability/ok");
        assertThat(((Number) JsonPath.read(event, "$.status")).intValue()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(event, "$.durationMs")).longValue()).isNotNegative();
        assertThat((String) JsonPath.read(event, "$.service.name")).isEqualTo("hippocampus-backend");
        assertThat((String) JsonPath.read(event, "$.service.environment")).isEqualTo("test");
        assertThat((String) JsonPath.read(event, "$.log.level")).isEqualTo("INFO");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatedCorrelationIdIsSharedByResponseAndLog(CapturedOutput output) throws Exception {
        var result = mockMvc.perform(get("/test/observability/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, "invalid-correlation-id"))
                .andExpect(status().isOk())
                .andReturn();

        var generated = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        assertThat((String) JsonPath.read(latestEvent(output, "http_request_completed"), "$.correlationId"))
                .isEqualTo(generated);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void correlationIdDoesNotLeakBetweenSequentialRequests(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/observability/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, FIRST_CORRELATION_ID))
                .andExpect(status().isOk());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();

        mockMvc.perform(get("/test/observability/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, SECOND_CORRELATION_ID))
                .andExpect(status().isOk());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();

        var events = events(output, "http_request_completed");
        assertThat(events).hasSize(2);
        assertThat((String) JsonPath.read(events.get(0), "$.correlationId"))
                .isEqualTo(FIRST_CORRELATION_ID);
        assertThat((String) JsonPath.read(events.get(1), "$.correlationId"))
                .isEqualTo(SECOND_CORRELATION_ID);
    }

    @Test
    void unexpectedErrorRetainsCorrelationAndCleansMdc(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/observability/fail")
                        .header(CorrelationIdFilter.HEADER_NAME, FIRST_CORRELATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, FIRST_CORRELATION_ID))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").value(FIRST_CORRELATION_ID));

        var errorEvent = latestEvent(output, "unhandled_exception");
        assertThat((String) JsonPath.read(errorEvent, "$.correlationId")).isEqualTo(FIRST_CORRELATION_ID);
        assertThat((String) JsonPath.read(errorEvent, "$.errorCode")).isEqualTo("INTERNAL_ERROR");

        var requestEvent = latestEvent(output, "http_request_completed");
        assertThat((String) JsonPath.read(requestEvent, "$.correlationId")).isEqualTo(FIRST_CORRELATION_ID);
        assertThat(((Number) JsonPath.read(requestEvent, "$.status")).intValue()).isEqualTo(500);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void requestCompletionDoesNotLogSensitiveRequestValues(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/test/observability/body")
                        .queryParam("search", QUERY_SECRET)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTHORIZATION_SECRET)
                        .header(HttpHeaders.COOKIE, "SESSION=" + COOKIE_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"" + BODY_SECRET + "\"}"))
                .andExpect(status().isOk());

        var event = latestEvent(output, "http_request_completed");
        assertThat((String) JsonPath.read(event, "$.requestPath"))
                .isEqualTo("/test/observability/body");
        assertThat(output.getAll()).doesNotContain(
                AUTHORIZATION_SECRET,
                COOKIE_SECRET,
                QUERY_SECRET,
                BODY_SECRET,
                "Authorization",
                "SESSION=");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private static String latestEvent(CapturedOutput output, String event) {
        var matchingEvents = events(output, event);
        assertThat(matchingEvents).isNotEmpty();
        return matchingEvents.getLast();
    }

    private static List<String> events(CapturedOutput output, String event) {
        var eventField = "\"event\":\"" + event + "\"";
        return output.getAll().lines()
                .filter(line -> line.contains(eventField))
                .toList();
    }

    @RestController
    @RequestMapping("/test/observability")
    static final class ObservabilityTestController {

        @GetMapping("/ok")
        void ok() {
        }

        @PostMapping("/body")
        void body(@RequestBody String ignored) {
        }

        @GetMapping("/fail")
        void fail() {
            throw new IllegalStateException("Synthetic observability failure");
        }
    }
}
