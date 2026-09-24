package com.funchole.backend.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single flag every part of the package/quota system (see
 * {@code PackageLimitService}) checks first. {@code false} (the default,
 * and what every self-hosted `docker-compose` install gets) means every
 * quota check is a no-op and the platform behaves exactly as it always
 * has - unlimited, single-admin-or-not, no packages involved. {@code true}
 * turns on self-registration via Google sign-in (see
 * {@code GoogleAuthService}) and quota enforcement on Function/Flow/
 * Gateway/Domain creation.
 */
@ConfigurationProperties(prefix = "app.cloud-mode")
public record CloudModeProperties(
        boolean enabled
) {
}
