package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.jayway.jsonpath.JsonPath;

import io.micrometer.core.instrument.MeterRegistry;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityApplicationTests {

    private static final List<String> RESTRICTED_ENDPOINTS = List.of(
            "/actuator",
            "/actuator/metrics",
            "/actuator/env",
            "/actuator/beans",
            "/actuator/configprops",
            "/actuator/info",
            "/actuator/flyway",
            "/actuator/heapdump",
            "/actuator/threaddump");

    @LocalServerPort
    private int port;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Environment environment;

    @Test
    void healthEndpointsAreStatusOnlyAndTemporaryHealthEndpointIsRemoved() throws Exception {
        var generalHealth = get("/actuator/health");
        var liveness = get("/actuator/health/liveness");
        var readiness = get("/actuator/health/readiness");

        assertGeneralHealthIsSafe(generalHealth);
        assertStatusOnlyUp(liveness);
        assertStatusOnlyUp(readiness);
        assertThat(get("/health").statusCode()).isEqualTo(404);
    }

    @Test
    void livenessAndReadinessFollowOnlyTheirDedicatedAvailabilityStates() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
            assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(503);
            assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);

            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
            assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
            assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(503);
        } finally {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    void onlyHealthIsExposedOverHttpAndJmxExposureIsDisabled() throws Exception {
        for (var endpoint : RESTRICTED_ENDPOINTS) {
            assertThat(get(endpoint).statusCode())
                    .as("Expected %s to be inaccessible", endpoint)
                    .isEqualTo(404);
        }

        assertThat(environment.getRequiredProperty("management.endpoints.jmx.exposure.exclude"))
                .isEqualTo("*");
    }

    @Test
    void micrometerCollectsBuiltInMetricsWithoutExposingMetricsEndpoint() throws Exception {
        assertThat(meterRegistry.find("jvm.memory.used").meters()).isNotEmpty();
        assertThat(meterRegistry.find("process.uptime").meters()).isNotEmpty();

        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);

        assertThat(meterRegistry.find("http.server.requests").meters()).isNotEmpty();
        assertThat(get("/actuator/metrics").statusCode()).isEqualTo(404);
    }

    @Test
    void observabilityFoundationStartsWithoutAnApplicationDataSource() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatusOnlyUp(HttpResponse<String> response) {
        Map<String, Object> responseBody = healthResponse(response);
        assertThat(responseBody).containsExactly(Map.entry("status", "UP"));
    }

    private static void assertGeneralHealthIsSafe(HttpResponse<String> response) {
        Map<String, Object> responseBody = healthResponse(response);
        assertThat(responseBody).containsOnlyKeys("status", "groups");
        assertThat(responseBody.get("status")).isEqualTo("UP");
        assertThat(((List<?>) responseBody.get("groups")).stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("liveness", "readiness");
    }

    private static Map<String, Object> healthResponse(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType)
                        .startsWith("application/vnd.spring-boot.actuator"));

        return JsonPath.read(response.body(), "$");
    }
}
