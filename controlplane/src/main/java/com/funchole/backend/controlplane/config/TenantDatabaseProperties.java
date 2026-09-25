package com.funchole.backend.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the separate tenant-db Postgres server (not the
 * primary {@code spring.datasource}, which holds FuncHole's own
 * control-plane schema) - {@code adminUsername}/{@code adminPassword} are a
 * superuser-equivalent role used only to run {@code CREATE ROLE}/
 * {@code CREATE DATABASE} at sign-up time (see
 * {@code TenantDatabaseProvisioningService}), never used for tenant traffic
 * itself.
 */
@ConfigurationProperties(prefix = "app.tenant-database")
public record TenantDatabaseProperties(
        String host,
        int port,
        String adminUsername,
        String adminPassword
) {
    public String adminJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/postgres";
    }
}
