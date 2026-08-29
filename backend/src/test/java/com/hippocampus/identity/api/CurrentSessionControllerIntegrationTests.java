package com.hippocampus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.HippocampusApplication;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;

class CurrentSessionControllerIntegrationTests {

    private static final String ME_PATH = "/api/auth/me";

    @Test
    void authenticatedSessionReturnsOnlyServerDerivedUserIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "student@example.test";
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(userId, email),
                null,
                List.of());

        try (Fixture fixture = fixture()) {
            MvcResult result = fixture.mvc().perform(get(ME_PATH)
                            .servletPath(ME_PATH)
                            .with(authentication(authentication)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(email, "password", "token", "session", "status", "createdAt", "updatedAt");
        }
    }

    @Test
    void unauthenticatedSessionIsRejectedWithStableAuthenticationProblem() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.mvc().perform(get(ME_PATH).servletPath(ME_PATH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    private static Fixture fixture() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(HippocampusApplication.class)
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
}
