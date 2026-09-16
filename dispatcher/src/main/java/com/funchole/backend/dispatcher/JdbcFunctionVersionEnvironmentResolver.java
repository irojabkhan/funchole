package com.funchole.backend.dispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcFunctionVersionEnvironmentResolver implements FunctionVersionEnvironmentResolver {

    private final DataSource dataSource;
    private final FunctionSecretReader secretReader;

    public JdbcFunctionVersionEnvironmentResolver(DataSource dataSource, FunctionSecretReader secretReader) {
        this.dataSource = dataSource;
        this.secretReader = secretReader;
    }

    @Override
    public Map<String, String> resolve(UUID functionVersionId) {
        try (Connection connection = dataSource.getConnection()) {
            Map<String, String> environment = new LinkedHashMap<>();
            loadEnvVars(connection, functionVersionId, environment);
            loadSecrets(connection, functionVersionId, environment);
            return Map.copyOf(environment);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load function version environment: " + functionVersionId, exception);
        }
    }

    @Override
    public Map<String, String> resolve(InvocationStepExecution stepExecution) {
        try (Connection connection = dataSource.getConnection()) {
            Map<String, String> environment = new LinkedHashMap<>();
            if (stepExecution.flowId() != null) {
                loadFlowEnvVars(connection, stepExecution.flowId(), environment);
                loadFlowSecrets(connection, stepExecution.flowId(), environment);
            }
            loadEnvVars(connection, stepExecution.componentVersionId(), environment);
            loadSecrets(connection, stepExecution.componentVersionId(), environment);
            return Map.copyOf(environment);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load step environment: " + stepExecution.id(), exception);
        }
    }

    private void loadFlowEnvVars(Connection connection, UUID flowId, Map<String, String> environment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.config_key, v.config_value
                FROM flow_environment_attachments a
                JOIN environment_profiles p ON p.id = a.environment_profile_id
                JOIN environment_profile_env_vars v ON v.environment_profile_id = p.id
                WHERE a.flow_id = ?
                  AND p.deleted_at IS NULL
                ORDER BY a.priority ASC, a.created_at ASC, v.config_key ASC
                """)) {
            statement.setObject(1, flowId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    environment.put(resultSet.getString("config_key"), resultSet.getString("config_value"));
                }
            }
        }
    }

    private void loadFlowSecrets(Connection connection, UUID flowId, Map<String, String> environment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.config_key, s.secret_ref
                FROM flow_environment_attachments a
                JOIN environment_profiles p ON p.id = a.environment_profile_id
                JOIN environment_profile_secrets s ON s.environment_profile_id = p.id
                WHERE a.flow_id = ?
                  AND p.deleted_at IS NULL
                ORDER BY a.priority ASC, a.created_at ASC, s.config_key ASC
                """)) {
            statement.setObject(1, flowId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String key = resultSet.getString("config_key");
                    String secretRef = resultSet.getString("secret_ref");
                    String secretValue = secretReader.read(secretRef);
                    if (secretValue == null) {
                        throw new IllegalStateException("Environment secret value is missing: " + secretRef);
                    }
                    environment.put(key, secretValue);
                }
            }
        }
    }

    private void loadEnvVars(Connection connection, UUID functionVersionId, Map<String, String> environment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT config_key, config_value
                FROM function_version_env_vars
                WHERE function_version_id = ?
                ORDER BY config_key
                """)) {
            statement.setObject(1, functionVersionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    environment.put(resultSet.getString("config_key"), resultSet.getString("config_value"));
                }
            }
        }
    }

    private void loadSecrets(Connection connection, UUID functionVersionId, Map<String, String> environment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT config_key, secret_ref
                FROM function_version_secrets
                WHERE function_version_id = ?
                ORDER BY config_key
                """)) {
            statement.setObject(1, functionVersionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String key = resultSet.getString("config_key");
                    String secretRef = resultSet.getString("secret_ref");
                    String secretValue = secretReader.read(secretRef);
                    if (secretValue == null) {
                        throw new IllegalStateException("Function secret value is missing: " + secretRef);
                    }
                    environment.put(key, secretValue);
                }
            }
        }
    }
}
