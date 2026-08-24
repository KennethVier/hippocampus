package com.hippocampus.bootstrap;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary P0-01 health probe. P0-08 replaces this controller with the approved
 * Actuator liveness and readiness endpoints.
 */
@RestController
final class HealthController {

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    HealthResponse health() {
        return new HealthResponse("UP");
    }

    private record HealthResponse(String status) {
    }
}
