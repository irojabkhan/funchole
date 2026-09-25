package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.TenantDatabaseProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.stereotype.Service;

/**
 * Runs the actual {@code CREATE ROLE}/{@code CREATE DATABASE} provisioning
 * on the separate tenant-db Postgres server (see {@code TenantDatabaseProperties})
 * for a freshly self-registered user's default {@code Database} (see
 * {@code CloudSignupService}). Deliberately outside JPA/Hibernate - a plain
 * autocommit JDBC connection, since {@code CREATE DATABASE} cannot run
 * inside a transaction block in Postgres.
 *
 * <p>{@code databaseName}/{@code username} are never caller-supplied - the
 * only caller ({@code CloudSignupService}) generates them from a fixed safe
 * charset (see {@code generateUniqueDatabaseIdentifier}), so this class
 * never needs to escape a SQL identifier (which JDBC placeholders can't
 * parameterize anyway). {@code password} is generated the same way, so it
 * is also safe to inline directly into the DDL string.
 */
@Service
public class TenantDatabaseProvisioningService {

    private final HikariDataSource dataSource;

    public TenantDatabaseProvisioningService(TenantDatabaseProperties tenantDatabaseProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenantDatabaseProperties.adminJdbcUrl());
        config.setUsername(tenantDatabaseProperties.adminUsername());
        config.setPassword(tenantDatabaseProperties.adminPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setPoolName("tenant-db-admin-pool");
        // Connect lazily, on first actual use, not at pool construction -
        // this bean is created eagerly at application startup, and a
        // transient tenant-db connectivity hiccup at that moment must not
        // fail the whole Spring context (login/API/MCP have nothing to do
        // with tenant database provisioning).
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
    }

    public void provisionDatabase(String databaseName, String username, String password) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE ROLE " + username + " WITH LOGIN PASSWORD '" + password + "'");
                statement.execute("CREATE DATABASE " + databaseName + " OWNER " + username);
                // Postgres grants CONNECT on every new database to PUBLIC by
                // default - close that so no other tenant's role can even
                // open a connection to this one.
                statement.execute("REVOKE CONNECT ON DATABASE " + databaseName + " FROM PUBLIC");
                statement.execute("GRANT CONNECT ON DATABASE " + databaseName + " TO " + username);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to provision tenant database '" + databaseName + "': " + exception.getMessage(), exception);
        }
    }

    @PreDestroy
    public void close() {
        dataSource.close();
    }
}
