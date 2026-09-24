package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.config.CloudModeProperties;
import com.funchole.backend.controlplane.config.CloudProvisioningProperties;
import com.funchole.backend.controlplane.config.GoogleAuthProperties;
import com.funchole.backend.controlplane.config.SecurityProperties;
import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.funchole.backend.controlplane.repository.PackageRepository;
import com.funchole.backend.controlplane.repository.UserPackageRepository;
import com.funchole.backend.controlplane.security.JwtService;
import com.funchole.backend.controlplane.security.JwtToken;
import com.funchole.backend.core.base.exception.ForbiddenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises both authorization paths {@link GoogleAuthService} makes,
 * directly against hand-built {@link GoogleIdToken.Payload} instances
 * rather than faking or mocking {@code GoogleIdTokenVerifier#verify},
 * which is Google's own SDK code, not this class's own logic. Uses real
 * repositories/services (backed by the test Postgres, matching this
 * module's own established pattern) rather than hand-faking them, since
 * they're full {@code JpaRepository}/multi-collaborator services, not
 * small app-owned interfaces worth faking.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
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
    private AppDomainRepository appDomainRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private UserPackageRepository userPackageRepository;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private JwtService jwtService;

    // ---------- self-hosted single-admin path (cloud mode off) ----------

    @Test
    void issuesATokenForTheAdminAccountWhenTheEmailIsAllowlisted() {
        GoogleAuthService service = selfHostedService(List.of("Me@Gmail.com"));

        JwtToken token = service.authenticateVerifiedPayload(payload("me@gmail.com", true));

        assertThat(token.token()).isNotBlank();
    }

    @Test
    void allowlistMatchIsCaseInsensitive() {
        GoogleAuthService service = selfHostedService(List.of("me@gmail.com"));

        JwtToken token = service.authenticateVerifiedPayload(payload("ME@GMAIL.COM", true));

        assertThat(token.token()).isNotBlank();
    }

    @Test
    void rejectsAnEmailNotOnTheAllowlist() {
        GoogleAuthService service = selfHostedService(List.of("me@gmail.com"));

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("someone-else@gmail.com", true)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("someone-else@gmail.com");
    }

    @Test
    void rejectsAnUnverifiedEmailEvenIfAllowlisted() {
        GoogleAuthService service = selfHostedService(List.of("me@gmail.com"));

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("me@gmail.com", false)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsEverythingWhenNoEmailsAreAllowlisted() {
        GoogleAuthService service = selfHostedService(List.of());

        assertThatThrownBy(() -> service.authenticateVerifiedPayload(payload("me@gmail.com", true)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- cloud self-registration path (cloud mode on) ----------

    @Test
    void firstTimeGoogleSignInSelfRegistersANewUserWithTheFreePackageAndADefaultGateway() {
        GoogleAuthService service = cloudService(verifiedPlatformDomain());
        String email = "new-cloud-user-" + UUID.randomUUID() + "@gmail.com";

        JwtToken token = service.authenticateOrRegisterCloudUser(payload(email, true));

        assertThat(token.token()).isNotBlank();
        AppUser created = appUserRepository.findByEmail(email).orElseThrow();
        assertThat(userPackageRepository.findByAppUser_Id(created.getId())).isPresent();
        List<Gateway> gateways = gatewayRepository.findAllByAppUser_Id(created.getId(), Pageable.unpaged()).getContent();
        assertThat(gateways).hasSize(1);
        assertThat(gateways.get(0).getName()).isEqualTo("Default Gateway");
    }

    @Test
    void secondSignInWithTheSameEmailLogsIntoTheSameAccountRatherThanRegisteringAgain() {
        GoogleAuthService service = cloudService(verifiedPlatformDomain());
        String email = "returning-cloud-user-" + UUID.randomUUID() + "@gmail.com";

        service.authenticateOrRegisterCloudUser(payload(email, true));
        AppUser firstAccount = appUserRepository.findByEmail(email).orElseThrow();

        service.authenticateOrRegisterCloudUser(payload(email, true));
        AppUser secondLookup = appUserRepository.findByEmail(email).orElseThrow();

        assertThat(secondLookup.getId()).isEqualTo(firstAccount.getId());
        assertThat(gatewayRepository.findAllByAppUser_Id(firstAccount.getId(), Pageable.unpaged()).getContent())
                .hasSize(1);
    }

    @Test
    void cloudSignInRejectsAnUnverifiedEmail() {
        GoogleAuthService service = cloudService(verifiedPlatformDomain());

        assertThatThrownBy(() -> service.authenticateOrRegisterCloudUser(payload("unverified@gmail.com", false)))
                .isInstanceOf(BadCredentialsException.class);
    }

    private GoogleAuthService selfHostedService(List<String> allowedEmails) {
        GoogleAuthProperties properties = new GoogleAuthProperties("test-client-id", allowedEmails);
        CloudSignupService unusedOnThisPath = cloudSignupService(new CloudProvisioningProperties(""));
        return new GoogleAuthService(
                null, properties, securityProperties, new CloudModeProperties(false),
                appUserRepository, unusedOnThisPath, jwtService);
    }

    private GoogleAuthService cloudService(AppDomain platformDomain) {
        GoogleAuthProperties properties = new GoogleAuthProperties("test-client-id", List.of());
        CloudSignupService cloudSignupService = cloudSignupService(
                new CloudProvisioningProperties(platformDomain.getId().toString()));
        return new GoogleAuthService(
                null, properties, securityProperties, new CloudModeProperties(true),
                appUserRepository, cloudSignupService, jwtService);
    }

    private CloudSignupService cloudSignupService(CloudProvisioningProperties provisioningProperties) {
        return new CloudSignupService(
                appUserRepository, userPackageRepository, packageRepository, appDomainRepository,
                gatewayService, passwordEncoder, provisioningProperties);
    }

    private AppDomain verifiedPlatformDomain() {
        AppUser admin = appUserRepository.findByUsername(securityProperties.bootstrapUser().username()).orElseThrow();
        return appDomainRepository.save(AppDomain.create(
                admin, "cloud-platform-" + UUID.randomUUID() + ".example.com", "verify-me", DomainStatus.VERIFIED));
    }

    private GoogleIdToken.Payload payload(String email, boolean emailVerified) {
        return new GoogleIdToken.Payload().setEmail(email).setEmailVerified(emailVerified);
    }
}
