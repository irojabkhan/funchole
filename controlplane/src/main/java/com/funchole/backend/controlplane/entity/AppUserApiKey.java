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
 * A long-lived credential a user generates for machine clients (the MCP
 * server, eventually the CLI) that can't hold the short-lived browser JWT.
 * Only {@code hashedKey} (SHA-256 of the raw key) is ever stored - the raw
 * key is returned to the caller once, at creation time, and never again.
 */
@Entity
@Table(name = "app_user_api_keys")
public class AppUserApiKey {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "hashed_key", nullable = false, length = 64)
    private String hashedKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public UUID getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getHashedKey() {
        return hashedKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void markUsed() {
        this.lastUsedAt = OffsetDateTime.now();
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }

    public static AppUserApiKey create(AppUser appUser, String name, String keyPrefix, String hashedKey) {
        AppUserApiKey apiKey = new AppUserApiKey();
        apiKey.id = UUID.randomUUID();
        apiKey.appUser = appUser;
        apiKey.name = name;
        apiKey.keyPrefix = keyPrefix;
        apiKey.hashedKey = hashedKey;
        apiKey.createdAt = OffsetDateTime.now();
        return apiKey;
    }
}
