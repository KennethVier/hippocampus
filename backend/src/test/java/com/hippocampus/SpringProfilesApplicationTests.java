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

class SpringProfilesApplicationTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileScenarios")
    void profileStartsWithExpectedNetworkConfiguration(ProfileScenario scenario) throws Exception {
        try (var context = new SpringApplicationBuilder(HippocampusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(scenario.profile())
                .run(scenario.arguments().toArray(String[]::new))) {
            var environment = context.getEnvironment();
            var assignedPort = environment.getRequiredProperty("local.server.port", Integer.class);

            assertThat(environment.getActiveProfiles()).containsExactly(scenario.profile());
            assertThat(environment.getRequiredProperty("server.address"))
                    .isEqualTo(scenario.expectedAddress());
            assertThat(environment.getRequiredProperty("server.port", Integer.class)).isZero();
            assertThat(assignedPort).isPositive();
            assertThat(environment.getRequiredProperty(
                    "logging.structured.ecs.service.environment"))
                    .isEqualTo(scenario.profile());

            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + assignedPort
                            + "/actuator/health/readiness"))
                    .GET()
                    .build();
            var response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    private static Stream<ProfileScenario> profileScenarios() {
        return Stream.of(
                new ProfileScenario("local", "127.0.0.1", List.of(
                        "--SERVER_PORT=0",
                        "--spring.flyway.enabled=false")),
                new ProfileScenario("test", "127.0.0.1", List.of(
                        "--spring.flyway.enabled=false")),
                new ProfileScenario("pilot", "0.0.0.0", List.of(
                        "--PORT=0",
                        "--spring.flyway.enabled=false")));
    }

    private record ProfileScenario(String profile, String expectedAddress, List<String> arguments) {
        @Override
        public String toString() {
            return profile;
        }
    }
}
