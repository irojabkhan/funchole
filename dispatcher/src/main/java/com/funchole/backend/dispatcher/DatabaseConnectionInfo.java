package com.funchole.backend.dispatcher;

/**
 * A resolved, ready-to-use database connection handed to a Runtime Worker
 * over IPC so a function's {@code context.db(name)} call never needs to
 * build the connection itself. {@code password} is plaintext, resolved
 * server-side from OpenBao before this record is built - the same trust
 * model already used for environment-variable secrets.
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
