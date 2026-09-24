package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.CloudModeProperties;
import com.funchole.backend.controlplane.config.GoogleAuthProperties;
import com.funchole.backend.controlplane.config.SecurityProperties;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.security.JwtService;
import com.funchole.backend.controlplane.security.JwtToken;
import com.funchole.backend.core.base.exception.ForbiddenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * "Sign in with Google" - two entirely different authorization models
 * depending on {@link CloudModeProperties#enabled()}, kept in one class
 * because both share the same token-verification step:
 *
 * <ul>
 *   <li><b>Cloud mode off</b> (the self-hosted default): the single-admin
 *   path this class originally shipped with, completely unchanged. FuncHole
 *   has exactly one seeded admin ({@link SecurityProperties.BootstrapUser})
 *   and no per-user roles, so a verified Google identity here is only ever
 *   a second way to authenticate AS that one account, gated by {@link
 *   GoogleAuthProperties#allowedEmails()} - this never creates a new
 *   {@link AppUser}.</li>
 *   <li><b>Cloud mode on</b>: real self-registration. A verified email
 *   already belonging to an {@link AppUser} logs in as that user; a new one
 *   is handed to {@code CloudSignupService} to create an account, assign
 *   the free package, and provision a default Gateway. {@code
 *   ADMIN_ALLOWED_GOOGLE_EMAILS} is not consulted at all on this path - it
 *   stays scoped to the self-hosted single-admin gate it was built for.</li>
 * </ul>
 *
 * Either way, the frontend obtains a signed Google ID token via Google
 * Identity Services (no OAuth2 authorization-code/redirect flow, no client
 * secret needed) and posts it here; this class verifies the token's
 * signature/expiry/audience before either path runs, and issues exactly
 * the same kind of JWT {@code AuthController#issueToken} issues for
 * password login.
 */
@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final GoogleAuthProperties googleAuthProperties;
    private final SecurityProperties securityProperties;
    private final CloudModeProperties cloudModeProperties;
    private final AppUserRepository appUserRepository;
    private final CloudSignupService cloudSignupService;
    private final JwtService jwtService;

    public GoogleAuthService(
            GoogleIdTokenVerifier verifier,
            GoogleAuthProperties googleAuthProperties,
            SecurityProperties securityProperties,
            CloudModeProperties cloudModeProperties,
            AppUserRepository appUserRepository,
            CloudSignupService cloudSignupService,
            JwtService jwtService
    ) {
        this.verifier = verifier;
        this.googleAuthProperties = googleAuthProperties;
        this.securityProperties = securityProperties;
        this.cloudModeProperties = cloudModeProperties;
        this.appUserRepository = appUserRepository;
        this.cloudSignupService = cloudSignupService;
        this.jwtService = jwtService;
    }

    public JwtToken authenticate(String idTokenString) {
        if (!cloudModeProperties.enabled() && !googleAuthProperties.isConfigured()) {
            throw new IllegalStateException(
                    "Google sign-in is not configured on this instance (GOOGLE_OAUTH_CLIENT_ID is not set)");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid Google sign-in token", exception);
        }
        if (idToken == null) {
            throw new BadCredentialsException("Invalid Google sign-in token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        return cloudModeProperties.enabled()
                ? authenticateOrRegisterCloudUser(payload)
                : authenticateVerifiedPayload(payload);
    }

    /**
     * Split from {@link #authenticate(String)} so the actual authorization
     * decision (verified-email check, allowlist match, admin lookup, JWT
     * issuance) is directly unit-testable against a hand-built {@link
     * GoogleIdToken.Payload} - without needing to fake or mock {@link
     * GoogleIdTokenVerifier#verify}, which is Google's own SDK code, not
     * this class's own logic.
     */
    JwtToken authenticateVerifiedPayload(GoogleIdToken.Payload payload) {
        String email = requireVerifiedEmail(payload);
        if (!googleAuthProperties.isAllowed(email)) {
            throw new ForbiddenException("This Google account (" + email + ") is not authorized for this FuncHole instance");
        }

        String adminUsername = securityProperties.bootstrapUser().username();
        AppUser appUser = appUserRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new IllegalStateException("Admin account not found: " + adminUsername));

        return jwtService.generateToken(appUser.getId(), appUser.getUsername(), appUser.isPasswordChangeRequired());
    }

    /**
     * Cloud-mode counterpart to {@link #authenticateVerifiedPayload} - same
     * split-out-for-testability reasoning, no allowlist involved.
     */
    JwtToken authenticateOrRegisterCloudUser(GoogleIdToken.Payload payload) {
        String email = requireVerifiedEmail(payload);

        AppUser appUser = appUserRepository.findByEmail(email)
                .orElseGet(() -> cloudSignupService.signUp(email, resolveDisplayName(payload, email)));

        return jwtService.generateToken(appUser.getId(), appUser.getUsername(), appUser.isPasswordChangeRequired());
    }

    private String requireVerifiedEmail(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Google account has no verified email");
        }
        return email;
    }

    private String resolveDisplayName(GoogleIdToken.Payload payload, String email) {
        Object name = payload.get("name");
        return name instanceof String nameString && !nameString.isBlank()
                ? nameString
                : email.substring(0, Math.max(email.indexOf('@'), 0));
    }
}
