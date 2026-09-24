package com.funchole.backend.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The id of an already-created, already-verified {@code AppDomain} that
 * every self-registered cloud user's auto-provisioned default Gateway
 * attaches to (see {@code CloudSignupService}) - e.g. a real
 * {@code apps.funchole.dev} the operator verifies once, by hand, through
 * the existing domain-creation + DNS TXT-record flow, using their own
 * account. Real DNS ownership can't be fabricated by this codebase, so
 * this is a required one-time manual setup step before
 * {@code CLOUD_MODE_ENABLED=true} actually works end to end - see
 * docs/development.md.
 *
 * <p>Kept as a plain {@code String} (parsed to {@code UUID} only where it's
 * actually used, in {@code CloudSignupService}) rather than binding
 * straight to {@code UUID} here - every deployment, including every
 * self-hosted one that never sets this at all, still needs this property
 * to bind cleanly from an empty default.
 */
@ConfigurationProperties(prefix = "app.cloud-mode")
public record CloudProvisioningProperties(
        String defaultDomainId
) {
}
