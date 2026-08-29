package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import com.hippocampus.identity.infrastructure.security.CorsProperties;

class SpringProfilesApplicationTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileScenarios")
    void profileStartsWithExpectedNetworkConfiguration(ProfileScenario scenario) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(HippocampusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(scenario.profile())
                .run(scenario.arguments().toArray(String[]::new))) {
            ConfigurableEnvironment environment = context.getEnvironment();
            Integer assignedPort = environment.getRequiredProperty("local.server.port", Integer.class);
            CorsProperties corsProperties = context.getBean(CorsProperties.class);

            assertThat(environment.getActiveProfiles()).containsExactly(scenario.profile());
            assertThat(environment.getRequiredProperty("server.address"))
                    .isEqualTo(scenario.expectedAddress());
            assertThat(environment.getRequiredProperty("server.port", Integer.class)).isZero();
            assertThat(assignedPort).isPositive();
            assertThat(environment.getRequiredProperty(
                    "logging.structured.ecs.service.environment"))
                    .isEqualTo(scenario.profile());
            assertThat(environment.getRequiredProperty(
                    "server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
            assertThat(environment.getRequiredProperty(
                    "server.servlet.session.cookie.same-site"))
                    .isEqualTo(scenario.expectedSameSite());
            assertThat(environment.getRequiredProperty(
                    "server.servlet.session.cookie.path"))
                    .isEqualTo("/api");
            assertThat(corsProperties.allowedOrigins()).containsExactlyElementsOf(scenario.expectedCorsOrigins());
            if (scenario.expectedSecure() == null) {
                assertThat(environment.getProperty(
                        "server.servlet.session.cookie.secure", Boolean.class)).isNull();
            } else {
                assertThat(environment.getRequiredProperty(
                        "server.servlet.session.cookie.secure", Boolean.class))
                        .isEqualTo(scenario.expectedSecure());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + assignedPort
                            + "/actuator/health/readiness"))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    private static Stream<ProfileScenario> profileScenarios() {
        return Stream.of(
                new ProfileScenario(
                        "local",
                        "127.0.0.1",
                        "lax",
                        null,
                        List.of("http://localhost:5173", "http://127.0.0.1:5173"),
                        List.of(
                                "--SERVER_PORT=0",
                                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                                "--spring.flyway.enabled=false")),
                new ProfileScenario(
                        "test",
                        "127.0.0.1",
                        "lax",
                        null,
                        List.of("http://localhost:5173"),
                        List.of("--spring.flyway.enabled=false")),
                new ProfileScenario(
                        "pilot",
                        "0.0.0.0",
                        "none",
                        true,
                        List.of("https://hippocampus-pilot.example.test"),
                        List.of(
                                "--PORT=0",
                                "--spring.flyway.enabled=false",
                                "--HIPPOCAMPUS_CORS_ALLOWED_ORIGINS=https://hippocampus-pilot.example.test")));
    }

    private record ProfileScenario(
            String profile,
            String expectedAddress,
            String expectedSameSite,
            Boolean expectedSecure,
            List<String> expectedCorsOrigins,
            List<String> arguments) {
        @Override
        public String toString() {
            return profile;
        }
    }
}
