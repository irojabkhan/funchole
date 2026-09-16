package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A managed external database connection: the user supplies host/port/
 * credentials once, FuncHole stores the password in OpenBao (never in
 * Postgres, matching {@link FunctionVersionSecret}) and hands FunctionVersions
 * that attach to it a ready, pooled client at invocation time - the function
 * author never builds the connection itself. Internal (FuncHole-provisioned)
 * databases are a deliberate later addition; every row here today is an
 * external connection the user registered.
 */
@Entity
@Table(name = "databases")
public class Database {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_secret_ref", nullable = false, length = 2048)
    private String passwordSecretRef;

    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public UUID getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordSecretRef() {
        return passwordSecretRef;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void update(String name, String host, int port, String databaseName, String username, boolean sslEnabled) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.sslEnabled = sslEnabled;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updatePasswordSecretRef(String passwordSecretRef) {
        this.passwordSecretRef = passwordSecretRef;
        this.updatedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static Database create(
            UUID id,
            AppUser appUser,
            String name,
            String type,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordSecretRef,
            boolean sslEnabled
    ) {
        Database database = new Database();
        OffsetDateTime now = OffsetDateTime.now();
        database.id = id;
        database.appUser = appUser;
        database.name = name;
        database.type = type;
        database.host = host;
        database.port = port;
        database.databaseName = databaseName;
        database.username = username;
        database.passwordSecretRef = passwordSecretRef;
        database.sslEnabled = sslEnabled;
        database.createdAt = now;
        database.updatedAt = now;
        return database;
    }
}
