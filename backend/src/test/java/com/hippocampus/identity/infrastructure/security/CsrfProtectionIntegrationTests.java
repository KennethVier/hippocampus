package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

import static org.springframework.http.HttpStatus.NO_CONTENT;

class CsrfProtectionIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String PASSWORD = "correct horse battery staple";

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @Test
    void preAuthenticationAcquisitionReturnsOnlyAClientTokenAndNoStore() throws Exception {
        try (var fixture = fixture()) {
            var result = fixture.mvc.perform(get("/api/auth/csrf").servletPath("/api/auth/csrf"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andReturn();

            assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.token")).isNotBlank();
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("session", "SESSION", "principal", "implementation");
            assertThat(result.getRequest().getSession(false)).isNotNull();
        }
    }

    @Test
    void loginWithoutOrWithAnInvalidTokenUsesTheSameSafeCsrfContract() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("csrf@example.test");

            assertCsrfFailure(fixture.mvc.perform(login("csrf@example.test", PASSWORD))
                    .andExpect(status().isForbidden()).andReturn(), "submitted-token");

            assertCsrfFailure(fixture.mvc.perform(login("csrf@example.test", PASSWORD)
                    .header("X-CSRF-TOKEN", "submitted-token"))
                    .andExpect(status().isForbidden()).andReturn(), "submitted-token");
        }
    }

    @Test
    void validTokenAllowsLoginButWrongCredentialsRemainAuthenticationFailure() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("valid-token@example.test");
            var acquired = acquire(fixture);

            fixture.mvc.perform(login("valid-token@example.test", "wrong password")
                    .session(acquired.session()).header("X-CSRF-TOKEN", acquired.token()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }
    }

    @Test
    void successfulLoginRotatesSessionInvalidatesOldTokenAndRequiresFreshTokenForMutation() throws Exception {
        try (var fixture = fixture()) {
            var userId = fixture.createUser("lifecycle@example.test");
            var preLogin = acquire(fixture);
            var preLoginSessionId = preLogin.session().getId();
            var login = fixture.mvc.perform(login("lifecycle@example.test", PASSWORD)
                    .session(preLogin.session()).header("X-CSRF-TOKEN", preLogin.token()))
                    .andExpect(status().isNoContent())
                    .andReturn();
            var postLogin = (MockHttpSession) login.getRequest().getSession(false);

            assertThat(postLogin).isNotNull();
            assertThat(postLogin.getId()).isNotEqualTo(preLoginSessionId);

            assertCsrfFailure(fixture.mvc.perform(mutation().session(postLogin)
                    .header("X-CSRF-TOKEN", preLogin.token()))
                    .andExpect(status().isForbidden()).andReturn(), preLogin.token());

            var fresh = acquire(fixture, postLogin);
            fixture.mvc.perform(mutation().session(postLogin).header("X-CSRF-TOKEN", fresh.token()))
                    .andExpect(status().isNoContent());
            fixture.mvc.perform(get("/api/test/authenticated").servletPath("/api/test/authenticated")
                    .session(postLogin))
                    .andExpect(status().isOk())
                    .andExpect(content().string(userId.toString()));
        }
    }

    @Test
    void authenticatedMutationsRejectMissingAndInvalidTokens() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("mutation@example.test");
            var postLogin = loginWithAcquiredToken(fixture, "mutation@example.test");

            assertCsrfFailure(fixture.mvc.perform(mutation().session(postLogin))
                    .andExpect(status().isForbidden()).andReturn(), null);
            assertCsrfFailure(fixture.mvc.perform(mutation().session(postLogin)
                    .header("X-CSRF-TOKEN", "invalid-token"))
                    .andExpect(status().isForbidden()).andReturn(), "invalid-token");
        }
    }

    @Test
    void csrfTokensAreBoundToTheirSessions() throws Exception {
        try (var fixture = fixture()) {
            fixture.createUser("session-a@example.test");
            fixture.createUser("session-b@example.test");
            var sessionA = acquire(fixture);
            var sessionB = acquire(fixture);

            assertCsrfFailure(fixture.mvc.perform(mutation().session(sessionB.session())
                    .header("X-CSRF-TOKEN", sessionA.token()))
                    .andExpect(status().isForbidden()).andReturn(), sessionA.token());
        }
    }

    @Test
    void safeProtectedGetRemainsAuthenticationRequiredRatherThanCsrfRejected() throws Exception {
        try (var fixture = fixture()) {
            fixture.mvc.perform(get("/api/test/authenticated").servletPath("/api/test/authenticated"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void ordinaryAuthorizationDenialUsesTheNonCsrfContract() throws Exception {
        try (var fixture = fixture()) {
            fixture.mvc.perform(get("/api/test/admin").servletPath("/api/test/admin").with(user("student")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(jsonPath("$.message").value("Access is denied."));
        }
    }

    @Test
    void csrfFailureDoesNotDiscloseSubmittedOrSessionValues() throws Exception {
        try (var fixture = fixture()) {
            var session = new MockHttpSession(null, "PRIVATE_SESSION_ID_VALUE");
            var submitted = "PRIVATE_SUBMITTED_CSRF_TOKEN";
            var result = fixture.mvc.perform(mutation().session(session).header("X-CSRF-TOKEN", submitted))
                    .andExpect(status().isForbidden())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).doesNotContain(submitted, session.getId());
            assertThat(result.getResponse().getContentAsString()).contains("CSRF_VALIDATION_FAILED");
        }
    }

    private static AcquiredToken acquire(Fixture fixture) throws Exception {
        return acquire(fixture, new MockHttpSession());
    }

    private static AcquiredToken acquire(Fixture fixture, MockHttpSession session) throws Exception {
        var result = fixture.mvc.perform(get("/api/auth/csrf").servletPath("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        var token = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.token");
        return new AcquiredToken((MockHttpSession) result.getRequest().getSession(false), token);
    }

    private static MockHttpSession loginWithAcquiredToken(Fixture fixture, String email) throws Exception {
        var acquired = acquire(fixture);
        return (MockHttpSession) fixture.mvc.perform(login(email, PASSWORD).session(acquired.session())
                .header("X-CSRF-TOKEN", acquired.token()))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private static void assertCsrfFailure(org.springframework.test.web.servlet.MvcResult result, String secret)
            throws Exception {
        var body = result.getResponse().getContentAsString();
        assertThat(body).contains("CSRF_VALIDATION_FAILED");
        assertThat(body).contains("CSRF validation failed.");
        assertThat(body).doesNotContain("expected", "session", "SESSION");
        if (secret != null) {
            assertThat(body).doesNotContain(secret);
        }
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").servletPath("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder mutation() {
        return post("/api/test/mutation").servletPath("/api/test/mutation");
    }

    private static Fixture fixture() throws Exception {
        var context = startApplicationWithFlyway(TestEndpointConfiguration.class);
        var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        return new Fixture(context, mvc);
    }

    private record AcquiredToken(MockHttpSession session, String token) {}

    private record Fixture(ConfigurableApplicationContext context, MockMvc mvc) implements AutoCloseable {
        UUID createUser(String email) {
            var user = context.getBean(UserRepository.class).saveAndFlush(
                    new UserEntity(email, "Student", UserStatus.ACTIVE));
            var hash = context.getBean(PasswordEncoder.class).encode(PASSWORD);
            context.getBean(PasswordCredentialRepository.class)
                    .saveAndFlush(new PasswordCredentialEntity(user.getId(), hash));
            return user.getId();
        }

        @Override
        public void close() {
            context.close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointConfiguration {
        @Bean
        TestCsrfController testCsrfController() {
            return new TestCsrfController();
        }

        @Bean
        TestAuthorizationController testAuthorizationController() {
            return new TestAuthorizationController();
        }

        @Bean
        @Order(1)
        SecurityFilterChain testAuthorizationSecurityChain(HttpSecurity http,
                AccessDeniedHandler accessDeniedHandler) throws Exception {
            return http.securityMatcher("/api/test/admin")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority("TEST_ADMIN"))
                    .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(accessDeniedHandler))
                    .build();
        }
    }

    @RestController
    static class TestCsrfController {
        @PostMapping("/api/test/mutation")
        @ResponseStatus(NO_CONTENT)
        void mutate() {}

        @GetMapping("/api/test/authenticated")
        String authenticated(org.springframework.security.core.Authentication authentication) {
            return ((HippocampusPrincipal) authentication.getPrincipal()).userId().toString();
        }
    }

    @RestController
    static class TestAuthorizationController {
        @GetMapping("/api/test/admin")
        String admin() {
            return "admin";
        }
    }
}
