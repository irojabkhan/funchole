package com.funchole.backend.dispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcFunctionVersionDatabaseResolver implements FunctionVersionDatabaseResolver {

    private final DataSource dataSource;
    private final FunctionSecretReader secretReader;

    public JdbcFunctionVersionDatabaseResolver(DataSource dataSource, FunctionSecretReader secretReader) {
        this.dataSource = dataSource;
        this.secretReader = secretReader;
    }

    @Override
    public List<DatabaseConnectionInfo> resolve(UUID functionVersionId) {
        try (Connection connection = dataSource.getConnection()) {
            Map<UUID, DatabaseConnectionInfo> databases = new LinkedHashMap<>();
            loadFunctionVersionDatabases(connection, functionVersionId, databases);
            return List.copyOf(databases.values());
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load function version databases: " + functionVersionId, exception);
        }
    }

    @Override
    public List<DatabaseConnectionInfo> resolve(InvocationStepExecution stepExecution) {
        try (Connection connection = dataSource.getConnection()) {
            Map<UUID, DatabaseConnectionInfo> databases = new LinkedHashMap<>();
            if (stepExecution.flowId() != null) {
                loadFlowDatabases(connection, stepExecution.flowId(), databases);
            }
            loadFunctionVersionDatabases(connection, stepExecution.componentVersionId(), databases);
            return List.copyOf(databases.values());
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load step databases: " + stepExecution.id(), exception);
        }
    }

    private void loadFlowDatabases(
            Connection connection,
            UUID flowId,
            Map<UUID, DatabaseConnectionInfo> databases
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT d.id, d.name, d.type, d.host, d.port, d.database_name, d.username, d.password_secret_ref, d.ssl_enabled
                FROM flow_database_attachments a
                JOIN databases d ON d.id = a.database_id
                WHERE a.flow_id = ?
                  AND d.deleted_at IS NULL
                ORDER BY a.created_at ASC, d.name ASC
                """)) {
            statement.setObject(1, flowId);
            loadDatabaseRows(statement, databases);
        }
    }

    private void loadFunctionVersionDatabases(
            Connection connection,
            UUID functionVersionId,
            Map<UUID, DatabaseConnectionInfo> databases
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT d.id, d.name, d.type, d.host, d.port, d.database_name, d.username, d.password_secret_ref, d.ssl_enabled
                FROM function_version_database_attachments a
                JOIN databases d ON d.id = a.database_id
                WHERE a.function_version_id = ?
                  AND d.deleted_at IS NULL
                ORDER BY d.name
                """)) {
            statement.setObject(1, functionVersionId);
            loadDatabaseRows(statement, databases);
        }
    }

    private void loadDatabaseRows(
            PreparedStatement statement,
            Map<UUID, DatabaseConnectionInfo> databases
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String passwordSecretRef = resultSet.getString("password_secret_ref");
                String password = secretReader.read(passwordSecretRef);
                if (password == null) {
                    throw new IllegalStateException("Database secret value is missing: " + passwordSecretRef);
                }
                databases.put(
                        resultSet.getObject("id", UUID.class),
                        new DatabaseConnectionInfo(
                                resultSet.getString("name"),
                                resultSet.getString("type"),
                                resultSet.getString("host"),
                                resultSet.getInt("port"),
                                resultSet.getString("database_name"),
                                resultSet.getString("username"),
                                password,
                                resultSet.getBoolean("ssl_enabled")
                        )
                );
            }
        }
    }
}
