package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.GatewayCreateRequest;
import com.funchole.backend.controlplane.dto.GatewayUpdateRequest;
import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.constant.GatewayStatus;
import com.funchole.backend.controlplane.constant.PackageLimitKey;
import com.funchole.backend.controlplane.entity.AppDomain;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Gateway;
import com.funchole.backend.controlplane.event.GatewayCertificateProvisionRequested;
import com.funchole.backend.controlplane.repository.GatewayRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GatewayService {
    private static final String UNIQUE_KEY_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int UNIQUE_KEY_LENGTH = 6;
    private static final int UNIQUE_KEY_MAX_ATTEMPTS = 20;

    private final GatewayRepository gatewayRepository;
    private final DomainService domainService;
    private final GatewayCertificateService gatewayCertificateService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PackageLimitService packageLimitService;
    private final SecureRandom secureRandom = new SecureRandom();

    public GatewayService(
            GatewayRepository gatewayRepository,
            DomainService domainService,
            GatewayCertificateService gatewayCertificateService,
            ApplicationEventPublisher applicationEventPublisher,
            PackageLimitService packageLimitService
    ) {
        this.gatewayRepository = gatewayRepository;
        this.domainService = domainService;
        this.gatewayCertificateService = gatewayCertificateService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.packageLimitService = packageLimitService;
    }

    public Page<Gateway> listGateways(UUID appUserId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, Gateway::getCreatedAt)
        );
        return gatewayRepository.findAllByAppUser_Id(appUserId, pageable);
    }

    public Gateway getGatewayById(UUID appUserId, UUID gatewayId) {
        return gatewayRepository.findByIdAndAppUser_Id(gatewayId, appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Gateway not found: " + gatewayId));
    }

    @Transactional
    public Gateway createGateway(AppUser appUser, GatewayCreateRequest request) {
        packageLimitService.enforce(appUser.getId(), PackageLimitKey.MAX_GATEWAYS,
                gatewayRepository.countByAppUser_Id(appUser.getId()));
        AppDomain appDomain = domainService.getDomainById(appUser.getId(), request.appDomainId());
        validateVerifiedDomain(appDomain);

        return provisionGateway(appUser, appDomain, request.name(), request.description(), request.status());
    }

    /**
     * System-driven provisioning for a freshly self-registered cloud user
     * (see {@code CloudSignupService}) - not a user request, so it
     * deliberately skips both the quota check above (this IS their quota's
     * one free gateway, not a request against it) and
     * {@code domainService.getDomainById}'s ownership check ({@code
     * platformDomain} belongs to the platform/operator, never to the new
     * user). Reuses the exact same key-generation and certificate-provisioning
     * logic as a normal user-created gateway.
     */
    @Transactional
    public Gateway createDefaultGateway(AppUser appUser, AppDomain platformDomain) {
        validateVerifiedDomain(platformDomain);
        return provisionGateway(appUser, platformDomain, "Default Gateway", "Auto-provisioned on sign-up", GatewayStatus.ACTIVE);
    }

    private Gateway provisionGateway(AppUser appUser, AppDomain appDomain, String name, String description, GatewayStatus status) {
        String uniqueKey = generateUniqueKey();

        Gateway gateway = Gateway.create(
                appUser,
                appDomain,
                name,
                uniqueKey,
                description,
                status
        );

        Gateway savedGateway = gatewayRepository.save(gateway);
        GatewayCertificateService.CertificateSyncResult certificateSyncResult =
                gatewayCertificateService.ensureCertificate(savedGateway);
        publishProvisioningIfRequired(certificateSyncResult);
        return savedGateway;
    }

    @Transactional
    public Gateway updateGateway(UUID appUserId, UUID gatewayId, GatewayUpdateRequest request) {
        Gateway gateway = getGatewayById(appUserId, gatewayId);
        AppDomain appDomain = domainService.getDomainById(appUserId, request.appDomainId());
        validateVerifiedDomain(appDomain);

        gateway.update(
                appDomain,
                request.name(),
                gateway.getUniqueKey(),
                request.description(),
                request.status()
        );

        Gateway savedGateway = gatewayRepository.save(gateway);
        GatewayCertificateService.CertificateSyncResult certificateSyncResult =
                gatewayCertificateService.ensureCertificate(savedGateway);
        publishProvisioningIfRequired(certificateSyncResult);
        return savedGateway;
    }

    @Transactional
    public void deleteGateway(UUID appUserId, UUID gatewayId) {
        Gateway gateway = getGatewayById(appUserId, gatewayId);
        gatewayRepository.delete(gateway);
    }

    private String generateUniqueKey() {
        for (int attempt = 0; attempt < UNIQUE_KEY_MAX_ATTEMPTS; attempt++) {
            String uniqueKey = randomAlphanumericKey();
            if (!gatewayRepository.existsByUniqueKey(uniqueKey)) {
                return uniqueKey;
            }
        }

        throw new IllegalStateException("Unable to generate a unique gateway key");
    }

    private String randomAlphanumericKey() {
        StringBuilder builder = new StringBuilder(UNIQUE_KEY_LENGTH);
        for (int index = 0; index < UNIQUE_KEY_LENGTH; index++) {
            int randomIndex = secureRandom.nextInt(UNIQUE_KEY_CHARACTERS.length());
            builder.append(UNIQUE_KEY_CHARACTERS.charAt(randomIndex));
        }
        return builder.toString();
    }

    private void publishProvisioningIfRequired(GatewayCertificateService.CertificateSyncResult certificateSyncResult) {
        if (certificateSyncResult.provisioningRequired()) {
            applicationEventPublisher.publishEvent(
                    new GatewayCertificateProvisionRequested(certificateSyncResult.certificate().getId())
            );
        }
    }

    private void validateVerifiedDomain(AppDomain appDomain) {
        if (appDomain.getStatus() != DomainStatus.VERIFIED) {
            throw new IllegalArgumentException("Gateway can only be created for a verified domain");
        }
    }
}
