package com.funchole.backend.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.funchole.backend.controlplane.config.CloudModeProperties;
import com.funchole.backend.controlplane.constant.PackageLimitKey;
import com.funchole.backend.controlplane.dto.FunctionCreateRequest;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.FunctionRepository;
import com.funchole.backend.controlplane.repository.PackageLimitRepository;
import com.funchole.backend.controlplane.repository.PackageRepository;
import com.funchole.backend.controlplane.repository.UserPackageOverrideRepository;
import com.funchole.backend.controlplane.repository.UserPackageRepository;
import com.funchole.backend.controlplane.entity.UserPackage;
import com.funchole.backend.core.base.exception.QuotaExceededException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers the two things worth testing directly: the override -> package ->
 * unlimited resolution order (the actual nuance in {@link
 * PackageLimitService}), and that {@link CloudModeProperties#enabled()}
 * being false makes the whole system inert regardless of what's in the
 * database. One end-to-end check through {@link FunctionService} confirms
 * the enforcement call site actually blocks a real creation - the other
 * three enforced services ({@code FlowService}/{@code GatewayService}/
 * {@code DomainService}) call {@code PackageLimitService.enforce(...)} the
 * same one-line way, reviewable directly in their diffs, so this isn't
 * repeated per resource type.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class PackageLimitServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("funchole")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PackageLimitRepository packageLimitRepository;

    @Autowired
    private UserPackageRepository userPackageRepository;

    @Autowired
    private UserPackageOverrideRepository userPackageOverrideRepository;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enforceIsANoOpWhenCloudModeIsDisabled() {
        AppUser user = freshCloudUser();
        PackageLimitService service = packageLimitService(false);

        assertThatCode(() -> service.enforce(user.getId(), PackageLimitKey.MAX_GATEWAYS, 999))
                .doesNotThrowAnyException();
    }

    @Test
    void enforceAllowsUsageBelowThePackageLimit() {
        AppUser user = assignFreePackage(freshCloudUser());
        PackageLimitService service = packageLimitService(true);

        // free package's own seeded MAX_FUNCTIONS is 100 - well below it.
        assertThatCode(() -> service.enforce(user.getId(), PackageLimitKey.MAX_FUNCTIONS, 5))
                .doesNotThrowAnyException();
    }

    @Test
    void enforceBlocksUsageAtThePackageLimit() {
        AppUser user = assignFreePackage(freshCloudUser());
        PackageLimitService service = packageLimitService(true);

        assertThatThrownBy(() -> service.enforce(user.getId(), PackageLimitKey.MAX_DOMAINS, 0))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("0");
    }

    @Test
    void perUserOverrideTakesPrecedenceOverThePackageLimit() {
        AppUser user = assignFreePackage(freshCloudUser());
        insertOverride(user.getId(), PackageLimitKey.MAX_GATEWAYS, 2);
        PackageLimitService service = packageLimitService(true);

        // free package's own MAX_GATEWAYS is 1 - the override of 2 wins.
        assertThatCode(() -> service.enforce(user.getId(), PackageLimitKey.MAX_GATEWAYS, 1))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.enforce(user.getId(), PackageLimitKey.MAX_GATEWAYS, 2))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void nullOverrideValueMeansUnlimitedForThatUser() {
        AppUser user = assignFreePackage(freshCloudUser());
        insertOverride(user.getId(), PackageLimitKey.MAX_FUNCTIONS, null);
        PackageLimitService service = packageLimitService(true);

        assertThatCode(() -> service.enforce(user.getId(), PackageLimitKey.MAX_FUNCTIONS, 100_000))
                .doesNotThrowAnyException();
    }

    @Test
    void functionServiceActuallyBlocksCreationOncePerUserOverrideLimitIsReached() {
        AppUser user = assignFreePackage(freshCloudUser());
        insertOverride(user.getId(), PackageLimitKey.MAX_FUNCTIONS, 1);
        FunctionService functionService = new FunctionService(functionRepository, packageLimitService(true));

        functionService.createFunction(user, new FunctionCreateRequest(
                "fn_quota_test_" + UUID.randomUUID().toString().replace("-", ""), "Quota Test Fn", null, "NODE"));

        assertThatThrownBy(() -> functionService.createFunction(user, new FunctionCreateRequest(
                "fn_quota_test_" + UUID.randomUUID().toString().replace("-", ""), "Quota Test Fn 2", null, "NODE")))
                .isInstanceOf(QuotaExceededException.class);
    }

    private PackageLimitService packageLimitService(boolean cloudModeEnabled) {
        return new PackageLimitService(
                new CloudModeProperties(cloudModeEnabled), userPackageRepository, packageLimitRepository, userPackageOverrideRepository);
    }

    private AppUser freshCloudUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        // saveAndFlush, not save: the raw JdbcTemplate insert in
        // insertOverride() below runs on the same transactional connection
        // but needs the FK row actually sent to Postgres first, not just
        // held in Hibernate's unflushed write-behind cache.
        return appUserRepository.saveAndFlush(AppUser.createFromGoogleSignUp(
                "quota_test_" + suffix, "quota-test-" + suffix + "@gmail.com", "Quota Test User",
                "$2a$10$unusable.placeholder.hash.for.tests.only........................"));
    }

    private AppUser assignFreePackage(AppUser user) {
        com.funchole.backend.controlplane.entity.Package freePackage = packageRepository.findByKey("free").orElseThrow();
        userPackageRepository.save(UserPackage.assign(user, freePackage.getId()));
        return user;
    }

    private void insertOverride(UUID appUserId, PackageLimitKey key, Integer value) {
        jdbcTemplate.update(
                "INSERT INTO user_package_overrides (id, app_user_id, limit_key, limit_value) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), appUserId, key.name(), value);
    }
}
