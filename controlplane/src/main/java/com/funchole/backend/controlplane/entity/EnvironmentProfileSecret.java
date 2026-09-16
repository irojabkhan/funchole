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

@Entity
@Table(name = "environment_profile_secrets")
public class EnvironmentProfileSecret {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_profile_id", nullable = false)
    private EnvironmentProfile environmentProfile;

    @Column(name = "config_key", nullable = false, length = 255)
    private String key;

    @Column(name = "secret_ref", nullable = false, length = 2048)
    private String secretRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public EnvironmentProfile getEnvironmentProfile() {
        return environmentProfile;
    }

    public String getKey() {
        return key;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateSecretRef(String secretRef) {
        this.secretRef = secretRef;
        this.updatedAt = OffsetDateTime.now();
    }

    public static EnvironmentProfileSecret create(EnvironmentProfile profile, String key, String secretRef) {
        EnvironmentProfileSecret secret = new EnvironmentProfileSecret();
        OffsetDateTime now = OffsetDateTime.now();
        secret.id = UUID.randomUUID();
        secret.environmentProfile = profile;
        secret.key = key;
        secret.secretRef = secretRef;
        secret.createdAt = now;
        secret.updatedAt = now;
        return secret;
    }
}
