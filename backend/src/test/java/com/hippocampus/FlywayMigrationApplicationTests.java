package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class FlywayMigrationApplicationTests extends PostgresIntegrationTestSupport {

    @Test
    void flywayMigratesFreshDatabaseFromZeroAndSecondStartupIsIdempotent() throws Exception {
        assertThat(isPostgresRunning()).isTrue();
        assertThat(postgresMappedPort()).isBetween(1, 65_535);
        assertThat(postgresJdbcUrl())
                .contains(postgresHost())
                .contains(":" + postgresMappedPort() + "/");
        assertDatabaseIsEmpty();

        try (var firstContext = startApplicationWithFlyway()) {
            assertThat(firstContext.isActive()).isTrue();
        }

        assertPostgresMajorVersion(18);
        assertExtensionExists("vector", "0.8.6");
        assertExtensionExists("pg_trgm", "1.6");
        assertSuccessfulFlywayVersion("1");
        assertNoFailedFlywayMigration();
        assertNoDomainTables();

        try (var secondContext = startApplicationWithFlyway()) {
            assertThat(secondContext.isActive()).isTrue();
        }

        assertSuccessfulFlywayVersion("1");
        assertNoFailedFlywayMigration();
        assertNoDomainTables();
    }

    private static void assertDatabaseIsEmpty() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertPostgresMajorVersion(int expectedMajorVersion) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW server_version_num")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1) / 10_000).isEqualTo(expectedMajorVersion);
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertExtensionExists(String extensionName, String expectedVersion)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT extversion
                        FROM pg_extension
                        WHERE extname = ?
                        """)) {
            statement.setString(1, extensionName);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected extension %s", extensionName)
                        .isTrue();
                assertThat(result.getString("extversion")).isEqualTo(expectedVersion);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertSuccessfulFlywayVersion(String version) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT success
                        FROM flyway_schema_history
                        WHERE version = ?
                        """)) {
            statement.setString(1, version);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected Flyway version %s", version)
                        .isTrue();
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertNoFailedFlywayMigration() throws SQLException {
        try (var connection = openPostgresConnection();
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

    private static void assertNoDomainTables() throws SQLException {
        try (var connection = openPostgresConnection();
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
                    .as("No Hippocampus domain tables should exist")
                    .isFalse();
        }
    }
}
