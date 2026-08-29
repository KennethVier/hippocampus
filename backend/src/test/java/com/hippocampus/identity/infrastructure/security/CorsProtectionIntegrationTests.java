package com.hippocampus.identity.infrastructure.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.HippocampusApplication;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

class CorsProtectionIntegrationTests {

    private static final String APPROVED_ORIGIN = "https://frontend.example.test";
    private static final String UNAPPROVED_ORIGIN = "https://attacker.example.test";

    @Test
    void approvedCredentialedPreflightReturnsOnlyTheConfiguredOrigin() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(options("/api/auth/login")
                            .servletPath("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, APPROVED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-CSRF-TOKEN"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APPROVED_ORIGIN))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, not("*")));
        }
    }

    @Test
    void approvedActualRequestExposesCorrelationIdForTheFrontend() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(get("/api/auth/csrf")
                            .servletPath("/api/auth/csrf")
                            .header(HttpHeaders.ORIGIN, APPROVED_ORIGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APPROVED_ORIGIN))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            containsString(CorrelationIdFilter.HEADER_NAME)));
        }
    }

    @Test
    void unapprovedOriginReceivesNoCredentialedCorsAccess() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(options("/api/auth/login")
                            .servletPath("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, UNAPPROVED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
    }

    @Test
    void disallowedMethodIsRejectedEvenFromAnApprovedOrigin() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(options("/api/auth/login")
                            .servletPath("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, APPROVED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "TRACE"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    void sameOriginRequestWithoutOriginHeaderRemainsUnaffected() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(get("/api/auth/csrf").servletPath("/api/auth/csrf"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    private static Fixture fixture() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(HippocampusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run("--hippocampus.security.cors.allowed-origins=" + APPROVED_ORIGIN);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        return new Fixture(context, mvc);
    }

    private record Fixture(ConfigurableApplicationContext context, MockMvc mvc) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }
}
