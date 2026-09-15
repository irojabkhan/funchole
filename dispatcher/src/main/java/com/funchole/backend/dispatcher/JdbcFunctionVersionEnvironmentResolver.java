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
                    if (environment.containsKey(key)) {
                        throw new IllegalStateException(
                                "Function version config key exists as both env var and secret: " + key);
                    }
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
