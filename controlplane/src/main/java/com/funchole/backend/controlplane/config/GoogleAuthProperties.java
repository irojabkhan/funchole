package com.funchole.backend.controlplane.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the "Sign in with Google" admin login path
 * ({@code GoogleAuthService}). Deliberately unvalidated (no
 * {@code @NotBlank}/{@code @Validated}) - unlike {@link SecurityProperties},
 * Google sign-in is optional: an instance that never sets
 * {@code GOOGLE_OAUTH_CLIENT_ID} simply never satisfies
 * {@code GoogleAuthService}'s own explicit "not configured" check, rather
 * than failing to start at all.
 *
 * <p>{@code allowedEmails} is the entire authorization model for this path:
 * FuncHole has exactly one bootstrap admin account and no per-user roles
 * (see {@code SecurityProperties.BootstrapUser}), so a verified Google
 * account is only ever a second, alternate way to sign in AS that one
 * account - never a path to creating a new one. Binds from a comma-separated
 * env var via Spring Boot's relaxed binding (e.g.
 * {@code ADMIN_ALLOWED_GOOGLE_EMAILS=me@gmail.com,ops@gmail.com}).
 */
@ConfigurationProperties(prefix = "app.google-auth")
public record GoogleAuthProperties(
        String clientId,
        List<String> allowedEmails
) {

    public GoogleAuthProperties {
        allowedEmails = allowedEmails == null ? List.of() : allowedEmails;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public boolean isAllowed(String email) {
        return email != null && allowedEmails.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(email));
    }
}
