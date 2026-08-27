package com.hippocampus.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.hippocampus.HippocampusApplication;

public abstract class PostgresIntegrationTestSupport {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("hippocampus_test")
            .withUsername("hippocampus")
            .withPassword("hippocampus");

    static {
        POSTGRES.start();
    }

    protected static boolean isPostgresRunning() {
        return POSTGRES.isRunning();
    }

    protected static String postgresJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String postgresHost() {
        return POSTGRES.getHost();
    }

    protected static int postgresMappedPort() {
        return POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    protected static Connection openPostgresConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    protected static void resetPostgresSchema() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    protected static ConfigurableApplicationContext startApplicationWithFlyway() {
        return new SpringApplicationBuilder(HippocampusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run(
                        "--spring.autoconfigure.exclude=",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.flyway.user=" + POSTGRES.getUsername(),
                        "--spring.flyway.password=" + POSTGRES.getPassword(),
                        "--spring.flyway.baseline-on-migrate=false",
                        "--server.port=0");
    }
}
