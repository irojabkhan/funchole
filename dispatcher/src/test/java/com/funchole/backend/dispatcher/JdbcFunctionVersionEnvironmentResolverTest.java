package com.funchole.backend.dispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Statement;
import java.time.OffsetDateTime;
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
            statement.execute("drop table if exists flow_environment_attachments");
            statement.execute("drop table if exists environment_profile_env_vars");
            statement.execute("drop table if exists environment_profile_secrets");
            statement.execute("drop table if exists environment_profiles");
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
            statement.execute("""
                    create table environment_profiles (
                        id UUID primary key,
                        deleted_at TIMESTAMP WITH TIME ZONE
                    )
                    """);
            statement.execute("""
                    create table environment_profile_env_vars (
                        id UUID primary key,
                        environment_profile_id UUID not null,
                        config_key VARCHAR(255) not null,
                        config_value TEXT not null
                    )
                    """);
            statement.execute("""
                    create table environment_profile_secrets (
                        id UUID primary key,
                        environment_profile_id UUID not null,
                        config_key VARCHAR(255) not null,
                        secret_ref VARCHAR(2048) not null
                    )
                    """);
            statement.execute("""
                    create table flow_environment_attachments (
                        id UUID primary key,
                        flow_id UUID not null,
                        environment_profile_id UUID not null,
                        priority INTEGER not null,
                        created_at TIMESTAMP WITH TIME ZONE not null default CURRENT_TIMESTAMP
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
    void resolvesFlowEnvironmentAndLetsFunctionVersionOverrideSharedValues() throws Exception {
        UUID flowId = UUID.randomUUID();
        UUID functionVersionId = UUID.randomUUID();
        UUID environmentProfileId = UUID.randomUUID();
        secretReader.put("environment-secret-ref", "shared-secret");
        secretReader.put("function-secret-ref", "function-secret");

        try (var connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into environment_profiles (id)
                    values ('%s')
                    """.formatted(environmentProfileId));
            statement.execute("""
                    insert into flow_environment_attachments (id, flow_id, environment_profile_id, priority)
                    values ('%s', '%s', '%s', 100)
                    """.formatted(UUID.randomUUID(), flowId, environmentProfileId));
            statement.execute("""
                    insert into environment_profile_env_vars (id, environment_profile_id, config_key, config_value)
                    values ('%s', '%s', 'NODE_ENV', 'production')
                    """.formatted(UUID.randomUUID(), environmentProfileId));
            statement.execute("""
                    insert into environment_profile_secrets (id, environment_profile_id, config_key, secret_ref)
                    values ('%s', '%s', 'SHARED_TOKEN', 'environment-secret-ref')
                    """.formatted(UUID.randomUUID(), environmentProfileId));
            statement.execute("""
                    insert into function_version_env_vars (id, function_version_id, config_key, config_value)
                    values ('%s', '%s', 'NODE_ENV', 'test')
                    """.formatted(UUID.randomUUID(), functionVersionId));
            statement.execute("""
                    insert into function_version_secrets (id, function_version_id, config_key, secret_ref)
                    values ('%s', '%s', 'API_TOKEN', 'function-secret-ref')
                    """.formatted(UUID.randomUUID(), functionVersionId));
        }

        InvocationStepExecution execution = new InvocationStepExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                flowId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "FUNCTION",
                UUID.randomUUID(),
                functionVersionId,
                "NODE",
                null,
                InvocationStepExecutionStatus.READY,
                1,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null
        );

        Map<String, String> environment = resolver.resolve(execution);

        assertEquals(Map.of(
                "NODE_ENV", "test",
                "SHARED_TOKEN", "shared-secret",
                "API_TOKEN", "function-secret"
        ), environment);
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
