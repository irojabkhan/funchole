package com.funchole.backend.controlplane.config;

import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Rotates the Flyway-seeded admin account's password away from its known
 * default ({@code admin12345}, baked into every fresh deployment by
 * {@code V1__create_app_inital_table.sql}) when an operator has actually
 * configured {@code BOOTSTRAP_PASSWORD} to something else.
 *
 * <p>Deliberately opt-in and idempotent: this only acts when the seeded
 * admin's stored hash still validates against the literal default password,
 * which is true on a fresh deployment and false forever after this runner
 * (or an operator manually changing it via the UI) has already rotated it -
 * so a later restart, or one where {@code BOOTSTRAP_PASSWORD} was never set
 * (every dev/test environment today), leaves the account untouched.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String SEEDED_ADMIN_USERNAME = "admin";
    private static final String SEEDED_ADMIN_DEFAULT_PASSWORD = "admin12345";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    public AdminBootstrapRunner(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String configuredPassword = securityProperties.bootstrapUser().password();
        if (SEEDED_ADMIN_DEFAULT_PASSWORD.equals(configuredPassword)) {
            // BOOTSTRAP_PASSWORD was never set (or was explicitly set back to
            // the default) - nothing to rotate.
            return;
        }

        appUserRepository.findByUsername(SEEDED_ADMIN_USERNAME).ifPresent(admin -> rotateIfStillDefault(admin, configuredPassword));
    }

    private void rotateIfStillDefault(AppUser admin, String newPassword) {
        if (!passwordEncoder.matches(SEEDED_ADMIN_DEFAULT_PASSWORD, admin.getPasswordHash())) {
            // Already rotated (by this runner on an earlier boot, or by the
            // operator through the UI) - never overwrite a real password.
            return;
        }

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setPasswordChangeRequired(false);
        appUserRepository.save(admin);
        logger.info("Rotated seeded admin account password from BOOTSTRAP_PASSWORD on first boot.");
    }
}
