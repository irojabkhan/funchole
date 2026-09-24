package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.CloudProvisioningProperties;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Package;
import com.funchole.backend.controlplane.entity.UserPackage;
import com.funchole.backend.controlplane.repository.AppDomainRepository;
import com.funchole.backend.controlplane.repository.AppUserRepository;
import com.funchole.backend.controlplane.repository.PackageRepository;
import com.funchole.backend.controlplane.repository.UserPackageRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration for the cloud product (see {@code GoogleAuthService},
 * which calls this the first time a verified Google email has never signed
 * in before). Atomic: a new {@link AppUser}, its {@code free} package
 * assignment, and its one auto-provisioned default {@code Gateway} are
 * created together or not at all - a real infra hiccup during gateway
 * provisioning fails the whole sign-up rather than leaving a half-created
 * account (a deliberate simplification for a first version; see the plan's
 * "explicitly out of scope" notes for revisiting this later).
 */
@Service
public class CloudSignupService {

    private static final String FREE_PACKAGE_KEY = "free";

    private final AppUserRepository appUserRepository;
    private final UserPackageRepository userPackageRepository;
    private final PackageRepository packageRepository;
    private final AppDomainRepository appDomainRepository;
    private final GatewayService gatewayService;
    private final PasswordEncoder passwordEncoder;
    private final CloudProvisioningProperties cloudProvisioningProperties;

    public CloudSignupService(
            AppUserRepository appUserRepository,
            UserPackageRepository userPackageRepository,
            PackageRepository packageRepository,
            AppDomainRepository appDomainRepository,
            GatewayService gatewayService,
            PasswordEncoder passwordEncoder,
            CloudProvisioningProperties cloudProvisioningProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.userPackageRepository = userPackageRepository;
        this.packageRepository = packageRepository;
        this.appDomainRepository = appDomainRepository;
        this.gatewayService = gatewayService;
        this.passwordEncoder = passwordEncoder;
        this.cloudProvisioningProperties = cloudProvisioningProperties;
    }

    @Transactional
    public AppUser signUp(String email, String fullName) {
        // Unguessable placeholder - this account only ever authenticates via
        // a verified Google ID token, never a password (see AppUser.createFromGoogleSignUp).
        String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        AppUser appUser = appUserRepository.save(
                AppUser.createFromGoogleSignUp(deriveUniqueUsername(email), email, fullName, passwordHash));

        Package freePackage = packageRepository.findByKey(FREE_PACKAGE_KEY)
                .orElseThrow(() -> new IllegalStateException(
                        "The '" + FREE_PACKAGE_KEY + "' package is not seeded - cannot complete cloud sign-up"));
        userPackageRepository.save(UserPackage.assign(appUser, freePackage.getId()));

        provisionDefaultGateway(appUser);

        return appUser;
    }

    private void provisionDefaultGateway(AppUser appUser) {
        String rawDomainId = cloudProvisioningProperties.defaultDomainId();
        if (rawDomainId == null || rawDomainId.isBlank()) {
            throw new IllegalStateException(
                    "CLOUD_DEFAULT_DOMAIN_ID is not configured - cannot provision a default gateway for new sign-ups");
        }
        UUID domainId;
        try {
            domainId = UUID.fromString(rawDomainId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CLOUD_DEFAULT_DOMAIN_ID is not a valid UUID: " + rawDomainId, exception);
        }
        AppDomain platformDomain = appDomainRepository.findById(domainId)
                .orElseThrow(() -> new IllegalStateException(
                        "Configured CLOUD_DEFAULT_DOMAIN_ID does not exist: " + domainId));
        gatewayService.createDefaultGateway(appUser, platformDomain);
    }

    /**
     * Google gives an email and a display name, not a username - derived
     * from the email's local-part, disambiguated with a numeric suffix on
     * collision (extremely unlikely for a fresh signup, but usernames are
     * unique - see AppUser).
     */
    private String deriveUniqueUsername(String email) {
        String localPart = email.substring(0, Math.max(email.indexOf('@'), 0));
        String base = localPart.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int suffix = 0;
        while (appUserRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }
}
