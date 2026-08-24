package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class FlywayMigrationApplicationTests {

    private static final String HOST = "127.0.0.1";
    private static final String PORT = System.getenv().getOrDefault("HIPPOCAMPUS_POSTGRES_PORT", "5432");
    private static final String USERNAME = "hippocampus";
    private static final String PASSWORD = "hippocampus";
    private static final String ADMIN_URL = jdbcUrl("postgres");
    private static final String LOCAL_DATABASE = "hippocampus";

    @Test
    void flywayMigratesFreshDatabaseFromZeroAndSecondStartupIsIdempotent() throws Exception {
        var databaseName = "hippocampus_p0_05_" + UUID.randomUUID().toString().replace("-", "_");

        try {
            createDatabase(databaseName);

            try (var firstContext = startContext(jdbcUrl(databaseName), "test",
                    "--spring.flyway.baseline-on-migrate=false")) {
                assertThat(firstContext.isActive()).isTrue();
            }

            assertExtensionExists(databaseName, "vector", "0.8.6");
            assertExtensionExists(databaseName, "pg_trgm", "1.6");
            assertSuccessfulFlywayVersion(databaseName, "1");
            assertNoFailedFlywayMigration(databaseName);
            assertNoDomainTables(databaseName);

            try (var secondContext = startContext(jdbcUrl(databaseName), "test",
                    "--spring.flyway.baseline-on-migrate=false")) {
                assertThat(secondContext.isActive()).isTrue();
            }

            assertSuccessfulFlywayVersion(databaseName, "1");
            assertNoFailedFlywayMigration(databaseName);
            assertNoDomainTables(databaseName);
        } finally {
            dropDatabaseIfExists(databaseName);
        }
    }

    @Test
    void flywayOnboardsExistingLocalDatabaseWithoutDomainTables() throws Exception {
        try (var context = startContext(jdbcUrl(LOCAL_DATABASE), "local", "--SERVER_PORT=0")) {
            assertThat(context.isActive()).isTrue();
        }

        assertExtensionExists(LOCAL_DATABASE, "vector", "0.8.6");
        assertExtensionExists(LOCAL_DATABASE, "pg_trgm", "1.6");
        assertSuccessfulFlywayVersion(LOCAL_DATABASE, "1");
        assertNoFailedFlywayMigration(LOCAL_DATABASE);
        assertNoDomainTables(LOCAL_DATABASE);
    }

    private static void createDatabase(String databaseName) throws SQLException {
        try (var connection = openAdminConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
    }

    private static void dropDatabaseIfExists(String databaseName) throws SQLException {
        try (var connection = openAdminConnection()) {
            try (var terminate = connection.prepareStatement("""
                    SELECT pg_terminate_backend(pid)
                    FROM pg_stat_activity
                    WHERE datname = ?
                      AND pid <> pg_backend_pid()
                    """)) {
                terminate.setString(1, databaseName);
                terminate.execute();
            }

            try (var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName));
            }
        }
    }

    private static AutoCloseableApplicationContext startContext(
            String datasourceUrl,
            String profile,
            String... additionalArguments) {
        var arguments = new String[additionalArguments.length + 5];
        arguments[0] = "--spring.flyway.url=" + datasourceUrl;
        arguments[1] = "--spring.flyway.user=" + USERNAME;
        arguments[2] = "--spring.flyway.password=" + PASSWORD;
        arguments[3] = "--spring.flyway.enabled=true";
        arguments[4] = "--server.port=0";
        System.arraycopy(additionalArguments, 0, arguments, 5, additionalArguments.length);

        return new AutoCloseableApplicationContext(new SpringApplicationBuilder(HippocampusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profile)
                .run(arguments));
    }

    private static void assertExtensionExists(String databaseName, String extensionName, String expectedVersion)
            throws SQLException {
        try (var connection = openDatabaseConnection(databaseName);
                var statement = connection.prepareStatement("""
                        SELECT extversion
                        FROM pg_extension
                        WHERE extname = ?
                        """)) {
            statement.setString(1, extensionName);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected extension %s in database %s", extensionName, databaseName)
                        .isTrue();
                assertThat(result.getString("extversion")).isEqualTo(expectedVersion);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertSuccessfulFlywayVersion(String databaseName, String version) throws SQLException {
        try (var connection = openDatabaseConnection(databaseName);
                var statement = connection.prepareStatement("""
                        SELECT success
                        FROM flyway_schema_history
                        WHERE version = ?
                        """)) {
            statement.setString(1, version);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected Flyway version %s in database %s", version, databaseName)
                        .isTrue();
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertNoFailedFlywayMigration(String databaseName) throws SQLException {
        try (var connection = openDatabaseConnection(databaseName);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*) AS failed_count
                        FROM flyway_schema_history
                        WHERE success = false
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("failed_count")).isZero();
        }
    }

    private static void assertNoDomainTables(String databaseName) throws SQLException {
        try (var connection = openDatabaseConnection(databaseName);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)) {
            assertThat(result.next())
                    .as("No Hippocampus domain tables should exist in database %s", databaseName)
                    .isFalse();
        }
    }

    private static java.sql.Connection openAdminConnection() throws SQLException {
        return openConnection(ADMIN_URL);
    }

    private static java.sql.Connection openDatabaseConnection(String databaseName) throws SQLException {
        return openConnection(jdbcUrl(databaseName));
    }

    private static java.sql.Connection openConnection(String url) throws SQLException {
        try {
            return DriverManager.getConnection(url, USERNAME, PASSWORD);
        } catch (SQLException exception) {
            throw new AssertionError("""
                    P0-05 migration tests require the P0-04 local PostgreSQL service.
                    Start it with: docker compose up -d postgres
                    Expected connection URL: %s
                    """.formatted(url), exception);
        }
    }

    private static String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + databaseName;
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private record AutoCloseableApplicationContext(
            org.springframework.context.ConfigurableApplicationContext delegate)
            implements AutoCloseable {

        boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
