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
@Table(name = "environment_profile_env_vars")
public class EnvironmentProfileEnvVar {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_profile_id", nullable = false)
    private EnvironmentProfile environmentProfile;

    @Column(name = "config_key", nullable = false, length = 255)
    private String key;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String value;

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

    public String getValue() {
        return value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateValue(String value) {
        this.value = value;
        this.updatedAt = OffsetDateTime.now();
    }

    public static EnvironmentProfileEnvVar create(EnvironmentProfile profile, String key, String value) {
        EnvironmentProfileEnvVar envVar = new EnvironmentProfileEnvVar();
        OffsetDateTime now = OffsetDateTime.now();
        envVar.id = UUID.randomUUID();
        envVar.environmentProfile = profile;
        envVar.key = key;
        envVar.value = value;
        envVar.createdAt = now;
        envVar.updatedAt = now;
        return envVar;
    }
}
