package com.funchole.backend.controlplane.service;

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
 * "Sign in with Google" for the single bootstrap admin account - not
 * self-registration. FuncHole has exactly one seeded admin ({@link
 * SecurityProperties.BootstrapUser}) and no per-user roles yet, so a
 * verified Google identity is only ever a second way to authenticate AS
 * that one account: this never creates a new {@link AppUser}. The frontend
 * obtains a signed Google ID token via Google Identity Services (no OAuth2
 * authorization-code/redirect flow, no client secret needed) and posts it
 * here; this class verifies the token's signature/expiry/audience, checks
 * the token's own verified email against {@link GoogleAuthProperties#allowedEmails()},
 * and - only on a match - issues exactly the same kind of JWT
 * {@code AuthController#issueToken} issues for password login.
 */
@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final GoogleAuthProperties googleAuthProperties;
    private final SecurityProperties securityProperties;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    public GoogleAuthService(
            GoogleIdTokenVerifier verifier,
            GoogleAuthProperties googleAuthProperties,
            SecurityProperties securityProperties,
            AppUserRepository appUserRepository,
            JwtService jwtService
    ) {
        this.verifier = verifier;
        this.googleAuthProperties = googleAuthProperties;
        this.securityProperties = securityProperties;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
    }

    public JwtToken authenticate(String idTokenString) {
        if (!googleAuthProperties.isConfigured()) {
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

        return authenticateVerifiedPayload(idToken.getPayload());
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
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Google account has no verified email");
        }
        if (!googleAuthProperties.isAllowed(email)) {
            throw new ForbiddenException("This Google account (" + email + ") is not authorized for this FuncHole instance");
        }

        String adminUsername = securityProperties.bootstrapUser().username();
        AppUser appUser = appUserRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new IllegalStateException("Admin account not found: " + adminUsername));

        return jwtService.generateToken(appUser.getId(), appUser.getUsername(), appUser.isPasswordChangeRequired());
    }
}
