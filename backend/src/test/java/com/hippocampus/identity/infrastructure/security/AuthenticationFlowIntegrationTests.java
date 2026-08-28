package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class AuthenticationFlowIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String PASSWORD = "correct horse battery staple";

    @BeforeEach void resetDatabase() throws Exception { resetPostgresSchema(); }

    @Test
    void successfulLoginPersistsPrincipalRotatesSessionAndRestoresContext() throws Exception {
        try (var fixture = fixture()) {
            var id = fixture.createUser("active@example.test", UserStatus.ACTIVE, true);
            var preLogin = new MockHttpSession();
            var oldId = preLogin.getId();
            var login = fixture.mvc.perform(login("active@example.test", PASSWORD).session(preLogin).with(csrf()))
                    .andExpect(status().isNoContent()).andReturn();

            var session = (MockHttpSession) login.getRequest().getSession(false);
            assertThat(session).isNotNull();
            assertThat(session.getId()).isNotEqualTo(oldId);
            var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(context.getAuthentication().getPrincipal())
                    .isEqualTo(new HippocampusPrincipal(id, "active@example.test"));
            assertThat(context.getAuthentication().getCredentials()).isNull();
            assertThat(context.getAuthentication().getAuthorities()).isEmpty();

            fixture.mvc.perform(apiGet("/api/test/authenticated").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(id.toString()));
        }
    }

    @Test
    void allAccountAndCredentialFailuresShareSafeExternalContract() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("wrong@example.test", UserStatus.ACTIVE, true);
            fixture.createUser("missing@example.test", UserStatus.ACTIVE, false);
            fixture.createUser("disabled@example.test", UserStatus.DISABLED, true);
            fixture.createUser("deleted@example.test", UserStatus.DELETED, true);

            fixture.assertAuthenticationFailure("wrong@example.test", "wrong password");
            fixture.assertAuthenticationFailure("unknown@example.test", PASSWORD);
            fixture.assertAuthenticationFailure("missing@example.test", PASSWORD);
            fixture.assertAuthenticationFailure("disabled@example.test", PASSWORD);
            fixture.assertAuthenticationFailure("deleted@example.test", PASSWORD);
        }
    }

    @Test
    void malformedAndInvalidRequestsAreSafeAndNeverAuthenticate() throws Exception {
        try (var fixture = fixture()) {
            var invalidBodies = new String[] {"{", "{}", "{\"email\":\"\",\"password\":\"x\"}",
                    "{\"email\":\"a@example.test\"}", "{\"email\":\"a@example.test\",\"password\":\"\"}"};
            for (var body : invalidBodies) {
                fixture.mvc.perform(apiPost("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
            }
            fixture.mvc.perform(apiPost("/api/auth/login").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("secret"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            fixture.mvc.perform(apiPost("/api/auth/login").with(csrf()).header("Content-Type", "not-a-media-type")
                            .content("{\"email\":\"a@example.test\",\"password\":\"secret\"}"))
                    .andExpect(status().isBadRequest()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            var longPassword = "é".repeat(37);
            assertThat(longPassword.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
            fixture.mvc.perform(login("a@example.test", longPassword).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void csrfAndUnauthenticatedApiContractsAreEnforced() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("csrf@example.test", UserStatus.ACTIVE, true);
            var rejected = fixture.mvc.perform(login("csrf@example.test", PASSWORD))
                    .andExpect(status().isForbidden()).andReturn();
            assertThat(rejected.getRequest().getUserPrincipal()).isNull();

            fixture.mvc.perform(apiGet("/api/test/authenticated"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void rateLimitContractIsEnforced() throws Exception {
        try (var fixture = fixtureWithRateLimit(2)) {
            fixture.mvc.perform(loginFrom("nobody@example.test", "wrong", "192.0.2.9"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
            fixture.mvc.perform(loginFrom("nobody@example.test", "wrong", "192.0.2.9"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
            fixture.mvc.perform(loginFrom("nobody@example.test", "wrong", "192.0.2.9"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_RATE_LIMITED"));
        }
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return apiPost("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder loginFrom(String email, String password, String remoteAddress) {
        return login(email, password).with(csrf()).with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        });
    }

    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post(path).servletPath(path);
    }

    private static MockHttpServletRequestBuilder apiGet(String path) {
        return get(path).servletPath(path);
    }

    private static Fixture fixture() throws Exception {
        var context = startApplicationWithFlyway(TestEndpointConfiguration.class);
        return fixture(context);
    }

    private static Fixture fixtureWithRateLimit(int maxAttempts) throws Exception {
        var context = startApplicationWithFlywayAndArguments(
                new Class<?>[] {TestEndpointConfiguration.class},
                "--hippocampus.security.login.max-attempts=" + maxAttempts);
        return fixture(context);
    }

    private static Fixture fixture(org.springframework.context.ConfigurableApplicationContext context) {
        var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        return new Fixture(context, mvc);
    }

    private record Fixture(org.springframework.context.ConfigurableApplicationContext context, MockMvc mvc)
            implements AutoCloseable {
        UUID createUser(String email, UserStatus status, boolean credential) {
            var user = context.getBean(UserRepository.class).saveAndFlush(new UserEntity(email, "Student", status));
            if (credential) {
                var hash = context.getBean(PasswordEncoder.class).encode(PASSWORD);
                context.getBean(PasswordCredentialRepository.class)
                        .saveAndFlush(new PasswordCredentialEntity(user.getId(), hash));
            }
            return user.getId();
        }

        void assertAuthenticationFailure(String email, String password) throws Exception {
            var result = mvc.perform(login(email, password).with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(password, "password_hash", "session", "JWT", "token", "DISABLED", "DELETED");
            assertThat(result.getRequest().getUserPrincipal()).isNull();
        }

        @Override public void close() { context.close(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointConfiguration {
        @Bean TestAuthenticatedController testAuthenticatedController() { return new TestAuthenticatedController(); }
    }

    @RestController
    static class TestAuthenticatedController {
        @GetMapping("/api/test/authenticated")
        String authenticated(org.springframework.security.core.Authentication authentication) {
            return ((HippocampusPrincipal) authentication.getPrincipal()).userId().toString();
        }
    }
}
