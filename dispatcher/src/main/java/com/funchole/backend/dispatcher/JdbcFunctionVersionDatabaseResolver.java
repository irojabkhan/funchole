package com.funchole.backend.dispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
            List<DatabaseConnectionInfo> databases = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT d.name, d.type, d.host, d.port, d.database_name, d.username, d.password_secret_ref, d.ssl_enabled
                    FROM function_version_database_attachments a
                    JOIN databases d ON d.id = a.database_id
                    WHERE a.function_version_id = ?
                    ORDER BY d.name
                    """)) {
                statement.setObject(1, functionVersionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String passwordSecretRef = resultSet.getString("password_secret_ref");
                        String password = secretReader.read(passwordSecretRef);
                        if (password == null) {
                            throw new IllegalStateException("Database secret value is missing: " + passwordSecretRef);
                        }
                        databases.add(new DatabaseConnectionInfo(
                                resultSet.getString("name"),
                                resultSet.getString("type"),
                                resultSet.getString("host"),
                                resultSet.getInt("port"),
                                resultSet.getString("database_name"),
                                resultSet.getString("username"),
                                password,
                                resultSet.getBoolean("ssl_enabled")
                        ));
                    }
                }
            }
            return List.copyOf(databases);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load function version databases: " + functionVersionId, exception);
        }
    }
}
