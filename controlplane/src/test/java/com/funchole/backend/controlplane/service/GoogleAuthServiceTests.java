package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.config.GoogleAuthProperties;
import com.funchole.backend.controlplane.config.SecurityProperties;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.security.JwtService;
import com.funchole.backend.controlplane.security.JwtToken;
import com.funchole.backend.core.base.exception.ForbiddenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@link GoogleAuthService#authenticateVerifiedPayload} - the
 * actual authorization decision this class makes - directly against
 * hand-built {@link GoogleIdToken.Payload} instances, rather than faking or
 * mocking {@code GoogleIdTokenVerifier#verify}, which is Google's own SDK
 * code. Uses a real {@link AppUserRepository}/{@link JwtService} (backed by
 * the test Postgres, matching this module's own established pattern) since
 * {@code AppUserRepository} is a full {@code JpaRepository} - not a small
 * app-owned interface worth hand-faking.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleAuthServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private JwtService jwtService;

    @Test
    void issuesATokenForTheAdminAccountWhenTheEmailIsAllowlisted() {
        GoogleAuthService service = service(List.of("Me@Gmail.com"));

        JwtToken token = service.authenticateVerifiedPayload(payload("me@gmail.com", true));

        assertThat(token.token()).isNotBlank();
    }

    @Test
    void allowlistMatchIsCaseInsensitive() {
        GoogleAuthService service = service(List.of("me@gmail.com"));

        JwtToken token = service.authenticateVerifiedPayload(payload("ME@GMAIL.COM", true));

        assertThat(token.token()).isNotBlank();
    }

    @Test
    void rejectsAnEmailNotOnTheAllowlist() {
        GoogleAuthService service = service(List.of("me@gmail.com"));

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("someone-else@gmail.com", true)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("someone-else@gmail.com");
    }

    @Test
    void rejectsAnUnverifiedEmailEvenIfAllowlisted() {
        GoogleAuthService service = service(List.of("me@gmail.com"));

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("me@gmail.com", false)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsEverythingWhenNoEmailsAreAllowlisted() {
        GoogleAuthService service = service(List.of());

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("me@gmail.com", true)))
                .isInstanceOf(ForbiddenException.class);
    }

    private GoogleAuthService service(List<String> allowedEmails) {
        GoogleAuthProperties properties = new GoogleAuthProperties("test-client-id", allowedEmails);
        return new GoogleAuthService(null, properties, securityProperties, appUserRepository, jwtService);
    }

    private GoogleIdToken.Payload payload(String email, boolean emailVerified) {
        return new GoogleIdToken.Payload().setEmail(email).setEmailVerified(emailVerified);
    }
}
