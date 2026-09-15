package com.funchole.backend.dispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcFunctionVersionEnvironmentResolverTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("funchole")
            .withUsername("funchole")
            .withPassword("funchole");

    private JdbcFunctionVersionEnvironmentResolver resolver;
    private RecordingSecretReader secretReader;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists function_version_env_vars");
            statement.execute("drop table if exists function_version_secrets");
            statement.execute("""
                    create table function_version_env_vars (
                        id UUID primary key,
                        function_version_id UUID not null,
                        config_key VARCHAR(255) not null,
                        config_value TEXT not null
                    )
                    """);
            statement.execute("""
                    create table function_version_secrets (
                        id UUID primary key,
                        function_version_id UUID not null,
                        config_key VARCHAR(255) not null,
                        secret_ref VARCHAR(2048) not null
                    )
                    """);
        }
        secretReader = new RecordingSecretReader();
        resolver = new JdbcFunctionVersionEnvironmentResolver(dataSource(), secretReader);
    }

    @Test
    void resolvesPlainEnvVarsAndSecretValuesForExactFunctionVersion() throws Exception {
        UUID functionVersionId = UUID.randomUUID();
        UUID otherVersionId = UUID.randomUUID();
        secretReader.put("function-versions/" + functionVersionId + "/secrets/API_TOKEN", "secret-token");

        try (var connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into function_version_env_vars (id, function_version_id, config_key, config_value)
                    values ('%s', '%s', 'NODE_ENV', 'test')
                    """.formatted(UUID.randomUUID(), functionVersionId));
            statement.execute("""
                    insert into function_version_env_vars (id, function_version_id, config_key, config_value)
                    values ('%s', '%s', 'NODE_ENV', 'other')
                    """.formatted(UUID.randomUUID(), otherVersionId));
            statement.execute("""
                    insert into function_version_secrets (id, function_version_id, config_key, secret_ref)
                    values ('%s', '%s', 'API_TOKEN', 'function-versions/%s/secrets/API_TOKEN')
                    """.formatted(UUID.randomUUID(), functionVersionId, functionVersionId));
        }

        Map<String, String> environment = resolver.resolve(functionVersionId);

        assertEquals(Map.of("NODE_ENV", "test", "API_TOKEN", "secret-token"), environment);
    }

    @Test
    void rejectsDuplicatePlainAndSecretConfigKey() throws Exception {
        UUID functionVersionId = UUID.randomUUID();
        secretReader.put("secret-ref", "secret-token");

        try (var connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into function_version_env_vars (id, function_version_id, config_key, config_value)
                    values ('%s', '%s', 'API_TOKEN', 'plain-token')
                    """.formatted(UUID.randomUUID(), functionVersionId));
            statement.execute("""
                    insert into function_version_secrets (id, function_version_id, config_key, secret_ref)
                    values ('%s', '%s', 'API_TOKEN', 'secret-ref')
                    """.formatted(UUID.randomUUID(), functionVersionId));
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> resolver.resolve(functionVersionId));

        assertEquals("Function version config key exists as both env var and secret: API_TOKEN", exception.getMessage());
    }

    private DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private static final class RecordingSecretReader implements FunctionSecretReader {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String read(String secretRef) {
            return values.get(secretRef);
        }

        void put(String secretRef, String value) {
            values.put(secretRef, value);
        }
    }
}
