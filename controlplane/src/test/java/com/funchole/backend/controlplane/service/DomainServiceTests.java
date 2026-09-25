package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funchole.backend.controlplane.config.CloudModeProperties;
import com.funchole.backend.controlplane.config.SecurityProperties;
import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.dto.DomainCreateRequest;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.core.base.exception.ForbiddenException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers the two behaviors specific to {@link DomainService} beyond plain
 * CRUD: domain creation is admin-only once cloud mode is on (the bootstrap
 * admin identified by {@link SecurityProperties#bootstrapUser()}, not a new
 * role/flag), and {@link DomainService#pickRandomVerifiedDomain()} - the
 * pool {@code GatewayService} draws from for a non-admin user's Gateway.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class DomainServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AppDomainRepository appDomainRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PackageLimitService packageLimitService;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void theBootstrapAdminCanCreateADomainWhenCloudModeIsEnabled() {
        DomainService service = domainService(true);
        AppUser admin = bootstrapAdmin();

        assertThatCode(() -> service.createDomain(admin, new DomainCreateRequest(uniqueDomainName())))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonAdminUserCannotCreateADomainWhenCloudModeIsEnabled() {
        DomainService service = domainService(true);
        AppUser nonAdmin = freshCloudUser();

        assertThatThrownBy(() -> service.createDomain(nonAdmin, new DomainCreateRequest(uniqueDomainName())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anyUserCanCreateADomainWhenCloudModeIsDisabled() {
        DomainService service = domainService(false);
        AppUser nonAdmin = freshCloudUser();

        assertThatCode(() -> service.createDomain(nonAdmin, new DomainCreateRequest(uniqueDomainName())))
                .doesNotThrowAnyException();
    }

    @Test
    void pickRandomVerifiedDomainReturnsAVerifiedDomain() {
        DomainService service = domainService(true);
        AppDomain verified = verifiedDomain();

        AppDomain picked = service.pickRandomVerifiedDomain();

        assertThat(picked.getStatus()).isEqualTo(DomainStatus.VERIFIED);
    }

    @Test
    void pickRandomVerifiedDomainFailsClearlyWhenNoneAreVerified() {
        // No domain created in this test - relies on @Transactional rolling
        // back every other test's domains, so this starts from empty.
        DomainService service = domainService(true);

        assertThatThrownBy(service::pickRandomVerifiedDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified domain");
    }

    private DomainService domainService(boolean cloudModeEnabled) {
        return new DomainService(
                appDomainRepository, packageLimitService, new CloudModeProperties(cloudModeEnabled), securityProperties);
    }

    private AppUser bootstrapAdmin() {
        return appUserRepository.findByUsername(securityProperties.bootstrapUser().username()).orElseThrow();
    }

    private AppUser freshCloudUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return appUserRepository.saveAndFlush(AppUser.createFromGoogleSignUp(
                "domain_test_" + suffix, "domain-test-" + suffix + "@gmail.com", "Domain Test User",
                "$2a$10$unusable.placeholder.hash.for.tests.only........................"));
    }

    private AppDomain verifiedDomain() {
        AppUser admin = bootstrapAdmin();
        return appDomainRepository.save(AppDomain.create(
                admin, uniqueDomainName(), "verify-me", DomainStatus.VERIFIED));
    }

    private String uniqueDomainName() {
        return "domain-test-" + UUID.randomUUID() + ".example.com";
    }
}
