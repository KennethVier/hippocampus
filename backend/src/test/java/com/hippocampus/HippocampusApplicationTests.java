package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HippocampusApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Test
    void applicationStartsAndHealthEndpointResponds() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health/liveness"))
                .GET()
                .build();

        var response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType)
                        .startsWith("application/vnd.spring-boot.actuator"));
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("UP");
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("hippocampus-backend");
    }
}
