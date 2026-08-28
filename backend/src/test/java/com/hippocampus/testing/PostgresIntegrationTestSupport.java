package com.hippocampus.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;

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
        return startApplicationWithFlyway(new Class<?>[0]);
    }

    protected static ConfigurableApplicationContext startApplicationWithFlyway(Class<?>... additionalSources) {
        return startApplicationWithFlywayAndArguments(additionalSources);
    }

    protected static ConfigurableApplicationContext startApplicationWithFlywayAndArguments(
            Class<?>[] additionalSources, String... additionalArguments) {
        var sources = new Class<?>[additionalSources.length + 1];
        sources[0] = HippocampusApplication.class;
        System.arraycopy(additionalSources, 0, sources, 1, additionalSources.length);
        var defaultArguments = new String[] {
                "--spring.autoconfigure.exclude=",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.enabled=true",
                "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
                "--spring.flyway.user=" + POSTGRES.getUsername(),
                "--spring.flyway.password=" + POSTGRES.getPassword(),
                "--spring.flyway.baseline-on-migrate=false",
                "--server.port=0"
        };
        var applicationArguments = Arrays.copyOf(defaultArguments, defaultArguments.length + additionalArguments.length);
        System.arraycopy(additionalArguments, 0, applicationArguments, defaultArguments.length, additionalArguments.length);
        return new SpringApplicationBuilder(sources)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run(applicationArguments);
    }
}
