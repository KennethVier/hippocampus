package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class SpringSessionJdbcIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "correct horse battery staple";

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void sessionCreationPersistsAuthenticatedContextAndCookieContract() throws Exception {
        try (var context = startApplication()) {
            var userId = createUser(context, "jdbc-session@example.test");
            var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = httpClient(cookieManager);

            var csrf = get(client, endpoint(context, "/api/auth/login"));
            assertThat(csrf.statusCode()).isEqualTo(200);
            var preLoginCookie = captureSessionCookie(csrf);

            var login = login(client, context, "jdbc-session@example.test", csrf.body().trim());
            assertThat(login.statusCode()).isEqualTo(204);
            assertThat(login.body()).isEmpty();
            var postLoginCookie = captureSessionCookie(login);
            assertThat(postLoginCookie.value()).isNotEqualTo(preLoginCookie.value());
            assertThat(postLoginCookie.value()).doesNotContain(userId.toString());
            assertBaseCookie(postLoginCookie);

            var row = sessionRow(userId);
            assertThat(row.principalName()).isEqualTo(userId.toString());
            assertThat(row.maxInactiveInterval()).isEqualTo(1_800);
            assertThat(securityContextAttributeCount(userId)).isEqualTo(1);
        }
    }

    @Test
    void authenticatedSessionSurvivesApplicationRestartWithSameDatabaseAndCookie() throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = httpClient(cookieManager);
        UUID userId;
        CapturedCookie capturedCookie;

        try (var applicationA = startApplication()) {
            userId = createUser(applicationA, "restart-session@example.test");
            var csrf = get(client, endpoint(applicationA, "/api/auth/login"));
            capturedCookie = captureSessionCookie(
                    login(client, applicationA, "restart-session@example.test", csrf.body().trim()));
            assertThat(capturedCookie.value()).doesNotContain(userId.toString());
            assertBaseCookie(capturedCookie);
            assertAuthenticated(client, applicationA, userId);
        }

        assertThat(cookieManager.getCookieStore().getCookies())
                .anyMatch(cookie -> cookie.getName().equals(capturedCookie.name())
                        && cookie.getValue().equals(capturedCookie.value()));

        try (var applicationB = startApplication()) {
            assertAuthenticated(client, applicationB, userId);
        }
    }

    @Test
    void expiredPersistedSessionCannotRestoreAuthentication() throws Exception {
        try (var context = startApplication()) {
            var userId = createUser(context, "expired-session@example.test");
            var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = httpClient(cookieManager);
            var csrf = get(client, endpoint(context, "/api/auth/login"));
            var login = login(client, context, "expired-session@example.test", csrf.body().trim());
            var cookie = captureSessionCookie(login);
            assertBaseCookie(cookie);
            assertThat(sessionRow(userId).principalName()).isEqualTo(userId.toString());

            expireSession(userId);

            var response = get(client, endpoint(context, "/api/test/authenticated"));
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("AUTHENTICATION_REQUIRED");
            assertThat(response.body()).doesNotContain(userId.toString());
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return startApplicationWithFlyway(TestSessionEndpoints.class);
    }

    private static UUID createUser(ConfigurableApplicationContext context, String email) {
        var user = context.getBean(UserRepository.class)
                .saveAndFlush(new UserEntity(email, "Student", UserStatus.ACTIVE));
        var hash = context.getBean(PasswordEncoder.class).encode(PASSWORD);
        context.getBean(PasswordCredentialRepository.class)
                .saveAndFlush(new PasswordCredentialEntity(user.getId(), hash));
        return user.getId();
    }

    private static HttpClient httpClient(CookieManager cookieManager) {
        return HttpClient.newBuilder().cookieHandler(cookieManager).build();
    }

    private static HttpResponse<String> get(HttpClient client, URI endpoint) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> login(HttpClient client, ConfigurableApplicationContext context,
            String email, String csrfToken) throws Exception {
        var request = HttpRequest.newBuilder(endpoint(context, "/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\""
                                + PASSWORD + "\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertAuthenticated(HttpClient client, ConfigurableApplicationContext context,
            UUID expectedUserId) throws Exception {
        var response = get(client, endpoint(context, "/api/test/authenticated"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expectedUserId.toString());
    }

    private static URI endpoint(ConfigurableApplicationContext context, String path) {
        var port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static CapturedCookie captureSessionCookie(HttpResponse<?> response) {
        var header = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> cookieAttribute(value, "Path").map("/api"::equals).orElse(false))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Expected a session Set-Cookie header"));
        var nameAndValue = header.substring(0, header.indexOf(';'));
        var separator = nameAndValue.indexOf('=');
        return new CapturedCookie(nameAndValue.substring(0, separator), nameAndValue.substring(separator + 1), header);
    }

    private static void assertBaseCookie(CapturedCookie cookie) {
        assertThat(cookie.name()).isNotBlank();
        assertThat(cookie.value()).isNotBlank();
        assertThat(cookie.header()).containsIgnoringCase("HttpOnly");
        assertThat(cookieAttribute(cookie.header(), "SameSite")).contains("Lax");
        assertThat(cookieAttribute(cookie.header(), "Path")).contains("/api");
        assertThat(cookieAttribute(cookie.header(), "Secure")).isEmpty();
    }

    private static Optional<String> cookieAttribute(String header, String attributeName) {
        return Arrays.stream(header.split(";"))
                .map(String::trim)
                .filter(attribute -> attribute.regionMatches(true, 0, attributeName, 0, attributeName.length()))
                .map(attribute -> attribute.length() == attributeName.length()
                        ? ""
                        : attribute.substring(attributeName.length()).replaceFirst("^=", ""))
                .findFirst();
    }

    private static SessionRow sessionRow(UUID userId) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                    SELECT PRINCIPAL_NAME, MAX_INACTIVE_INTERVAL
                    FROM SPRING_SESSION
                    WHERE PRINCIPAL_NAME = ?
                    """)) {
            statement.setString(1, userId.toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                var row = new SessionRow(result.getString("PRINCIPAL_NAME"),
                        result.getInt("MAX_INACTIVE_INTERVAL"));
                assertThat(result.next()).isFalse();
                return row;
            }
        }
    }

    private static int securityContextAttributeCount(UUID userId) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM SPRING_SESSION_ATTRIBUTES attributes
                    JOIN SPRING_SESSION sessions
                      ON sessions.PRIMARY_ID = attributes.SESSION_PRIMARY_ID
                    WHERE sessions.PRINCIPAL_NAME = ?
                      AND attributes.ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'
                    """)) {
            statement.setString(1, userId.toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static void expireSession(UUID userId) throws SQLException {
        var now = System.currentTimeMillis();
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                    UPDATE SPRING_SESSION
                    SET LAST_ACCESS_TIME = ?, EXPIRY_TIME = ?
                    WHERE PRINCIPAL_NAME = ?
                    """)) {
            statement.setLong(1, now - 3_600_000L);
            statement.setLong(2, now - 1L);
            statement.setString(3, userId.toString());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private record CapturedCookie(String name, String value, String header) {}

    private record SessionRow(String principalName, int maxInactiveInterval) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSessionEndpoints {
        @Bean
        TestSessionController testSessionController() {
            return new TestSessionController();
        }
    }

    @RestController
    static class TestSessionController {
        @GetMapping("/api/auth/login")
        String csrf(HttpServletRequest request) {
            var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return token.getToken();
        }

        @GetMapping("/api/test/authenticated")
        String authenticated(Authentication authentication) {
            return ((HippocampusPrincipal) authentication.getPrincipal()).userId().toString();
        }
    }
}
