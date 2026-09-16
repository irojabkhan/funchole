package com.funchole.backend.runtime;

/**
 * Runtime module's independent copy of the Dispatcher's
 * {@code DatabaseConnectionInfo} wire shape - deserialized from the same
 * JSON field names, no shared Java type across the IPC boundary.
 */
public record DatabaseConnectionInfo(
        String name,
        String type,
        String host,
        int port,
        String databaseName,
        String username,
        String password,
        boolean sslEnabled
) {
}
