package com.funchole.backend.gateway.server;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gateway's own dependencies it can't serve real traffic without -
 * Postgres (routing/registry data) and NATS (invocation dispatch/
 * completion). {@code /health} used to be a static "ok" regardless of
 * whether either was actually reachable.
 */
public final class GatewayHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(GatewayHealthChecker.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final io.nats.client.Connection natsConnection;

    public GatewayHealthChecker(DataSource dataSource, io.nats.client.Connection natsConnection) {
        this.dataSource = dataSource;
        this.natsConnection = natsConnection;
    }

    public Status check() {
        return new Status(checkDatabase(), checkNats());
    }

    private boolean checkDatabase() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException exception) {
            logger.warn("Database health check failed", exception);
            return false;
        }
    }

    private boolean checkNats() {
        return natsConnection.getStatus() == io.nats.client.Connection.Status.CONNECTED;
    }

    public record Status(boolean databaseHealthy, boolean natsHealthy) {
        public boolean healthy() {
            return databaseHealthy && natsHealthy;
        }
    }
}
