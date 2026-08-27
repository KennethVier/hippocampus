package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class FlywayMigrationApplicationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

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
        assertSuccessfulFlywayVersion("2");
        assertSuccessfulFlywayVersion("3");
        assertNoFailedFlywayMigration();
        assertDomainTablesExist();
        assertUsersColumnsMatchContract();
        assertUsersPrimaryKey();
        assertUsersEmailUniqueConstraint();
        assertUsersHasNoCheckConstraint();
        assertPasswordCredentialContract();

        try (var secondContext = startApplicationWithFlyway()) {
            assertThat(secondContext.isActive()).isTrue();
        }

        assertSuccessfulFlywayVersion("1");
        assertSuccessfulFlywayVersion("2");
        assertSuccessfulFlywayVersion("3");
        assertNoFailedFlywayMigration();
        assertDomainTablesExist();
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

    private static void assertDomainTablesExist() throws SQLException {
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
            assertThat(result.next()).isTrue();
            assertThat(result.getString("table_name")).isEqualTo("user_password_credentials");
            assertThat(result.next()).isTrue();
            assertThat(result.getString("table_name")).isEqualTo("users");
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertPasswordCredentialContract() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                    SELECT column_name, data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='user_password_credentials'
                    ORDER BY ordinal_position
                    """)) {
            var actual = new java.util.LinkedHashMap<String, String>();
            while (result.next()) actual.put(result.getString(1), result.getString(2) + ":" + result.getString(3));
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "user_id", "uuid:NO", "password_hash", "character varying:NO",
                    "created_at", "timestamp with time zone:NO", "updated_at", "timestamp with time zone:NO"));
        }
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                    SELECT confdeltype FROM pg_constraint
                    WHERE conrelid='public.user_password_credentials'::regclass AND contype='f'
                    """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("c");
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertUsersColumnsMatchContract() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT column_name, data_type, is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'users'
                        ORDER BY ordinal_position
                        """)) {
            var expected = Map.of(
                    "id", "uuid:NO",
                    "email", "character varying:NO",
                    "display_name", "character varying:YES",
                    "status", "character varying:NO",
                    "created_at", "timestamp with time zone:NO",
                    "updated_at", "timestamp with time zone:NO");
            var actual = new java.util.LinkedHashMap<String, String>();
            while (result.next()) {
                actual.put(result.getString("column_name"),
                        result.getString("data_type") + ":" + result.getString("is_nullable"));
            }
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    private static void assertUsersPrimaryKey() throws SQLException {
        assertConstraintColumns("PRIMARY KEY", Set.of("id"));
    }

    private static void assertUsersEmailUniqueConstraint() throws SQLException {
        assertConstraintColumns("UNIQUE", Set.of("email"));
    }

    private static void assertUsersHasNoCheckConstraint() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM pg_constraint
                        WHERE conrelid = 'public.users'::regclass
                          AND contype = 'c'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    private static void assertConstraintColumns(String constraintType, Set<String> expectedColumns)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_catalog = kcu.constraint_catalog
                         AND tc.constraint_schema = kcu.constraint_schema
                         AND tc.constraint_name = kcu.constraint_name
                        WHERE tc.table_schema = 'public'
                          AND tc.table_name = 'users'
                          AND tc.constraint_type = ?
                        """)) {
            statement.setString(1, constraintType);
            try (var result = statement.executeQuery()) {
                var actual = new java.util.HashSet<String>();
                while (result.next()) {
                    actual.add(result.getString("column_name"));
                }
                assertThat(actual).isEqualTo(expectedColumns);
            }
        }
    }
}
