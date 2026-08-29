package com.hippocampus.identity.infrastructure.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.HippocampusApplication;
import com.hippocampus.identity.port.CurrentUser;

class CurrentUserIntegrationTests {

    @Test
    void clientSuppliedUserIdCannotOverrideAuthenticatedOwnershipRoot() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID clientSuppliedUserId = UUID.randomUUID();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(authenticatedUserId, "student@example.test"),
                null,
                List.of());

        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(get("/api/test/current-user")
                            .servletPath("/api/test/current-user")
                            .param("userId", clientSuppliedUserId.toString())
                            .with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(authenticatedUserId.toString()));
        }
    }

    @Test
    void unauthenticatedRequestCannotResolveCurrentUser() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(get("/api/test/current-user")
                            .servletPath("/api/test/current-user")
                            .param("userId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    private static Fixture fixture() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(HippocampusApplication.class)
                .sources(TestEndpointConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run();
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointConfiguration {

        @Bean
        TestCurrentUserController testCurrentUserController(CurrentUser currentUser) {
            return new TestCurrentUserController(currentUser);
        }
    }

    @RestController
    static final class TestCurrentUserController {

        private final CurrentUser currentUser;

        TestCurrentUserController(CurrentUser currentUser) {
            this.currentUser = currentUser;
        }

        @GetMapping("/api/test/current-user")
        String currentUser(@RequestParam("userId") UUID ignoredClientUserId) {
            return currentUser.authenticatedUser().userId().toString();
        }
    }
}
