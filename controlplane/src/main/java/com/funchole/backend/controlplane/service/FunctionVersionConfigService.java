package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FunctionVersionConfigResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionEnvVarResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSecretResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FunctionVersionEnvVar;
import com.funchole.backend.controlplane.entity.FunctionVersionSecret;
import com.funchole.backend.controlplane.repository.FunctionVersionEnvVarRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionSecretRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FunctionVersionConfigService {

    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final FunctionVersionService functionVersionService;
    private final FunctionVersionEnvVarRepository envVarRepository;
    private final FunctionVersionSecretRepository secretRepository;
    private final FunctionSecretStore functionSecretStore;

    public FunctionVersionConfigService(
            FunctionVersionService functionVersionService,
            FunctionVersionEnvVarRepository envVarRepository,
            FunctionVersionSecretRepository secretRepository,
            FunctionSecretStore functionSecretStore
    ) {
        this.functionVersionService = functionVersionService;
        this.envVarRepository = envVarRepository;
        this.secretRepository = secretRepository;
        this.functionSecretStore = functionSecretStore;
    }

    @Transactional(readOnly = true)
    public FunctionVersionConfigResponse getConfig(UUID appUserId, UUID functionId, UUID versionId) {
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        return toResponse(functionVersion);
    }

    /**
     * Copies every env var and secret from {@code source} onto {@code target}
     * - used by {@code FunctionVersionCloneService} when a new version is
     * auto-seeded from the Function's most recent version. Secrets are
     * re-encrypted under the target version's own OpenBao path (read back via
     * {@link FunctionSecretStore#readSecretValue}, then written fresh via
     * {@link FunctionSecretStore#save}) rather than sharing the source
     * version's secret ref, since each version's secrets are independently
     * addressed by its own id.
     */
    @Transactional
    public void cloneConfig(FunctionVersion source, FunctionVersion target) {
        for (FunctionVersionEnvVar envVar : envVarRepository.findAllByFunctionVersion_IdOrderByKeyAsc(source.getId())) {
            envVarRepository.save(FunctionVersionEnvVar.create(target, envVar.getKey(), envVar.getValue()));
        }
        for (FunctionVersionSecret secret : secretRepository.findAllByFunctionVersion_IdOrderByKeyAsc(source.getId())) {
            String value = functionSecretStore.readSecretValue(secret.getSecretRef());
            String newSecretRef = functionSecretStore.save(target.getId(), secret.getKey(), value);
            secretRepository.save(FunctionVersionSecret.create(target, secret.getKey(), newSecretRef));
        }
    }

    @Transactional
    public FunctionVersionConfigResponse upsertEnvVar(
            UUID appUserId,
            UUID functionId,
            UUID versionId,
            String key,
            String value
    ) {
        validateKey(key);
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        if (secretRepository.findByFunctionVersion_IdAndKey(versionId, key).isPresent()) {
            throw new IllegalArgumentException("Config key already exists as a secret: " + key);
        }
        FunctionVersionEnvVar envVar = envVarRepository.findByFunctionVersion_IdAndKey(versionId, key)
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> FunctionVersionEnvVar.create(functionVersion, key, value));
        envVarRepository.save(envVar);
        return toResponse(functionVersion);
    }

    @Transactional
    public FunctionVersionConfigResponse upsertSecret(
            UUID appUserId,
            UUID functionId,
            UUID versionId,
            String key,
            String value
    ) {
        validateKey(key);
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        if (envVarRepository.findByFunctionVersion_IdAndKey(versionId, key).isPresent()) {
            throw new IllegalArgumentException("Config key already exists as an env var: " + key);
        }
        String secretRef = functionSecretStore.save(versionId, key, value);
        FunctionVersionSecret secret = secretRepository.findByFunctionVersion_IdAndKey(versionId, key)
                .map(existing -> {
                    existing.updateSecretRef(secretRef);
                    return existing;
                })
                .orElseGet(() -> FunctionVersionSecret.create(functionVersion, key, secretRef));
        secretRepository.save(secret);
        return toResponse(functionVersion);
    }

    private FunctionVersionConfigResponse toResponse(FunctionVersion functionVersion) {
        UUID functionVersionId = functionVersion.getId();
        return new FunctionVersionConfigResponse(
                functionVersionId,
                envVarRepository.findAllByFunctionVersion_IdOrderByKeyAsc(functionVersionId)
                        .stream()
                        .map(this::toEnvVarResponse)
                        .toList(),
                secretRepository.findAllByFunctionVersion_IdOrderByKeyAsc(functionVersionId)
                        .stream()
                        .map(this::toSecretResponse)
                        .toList()
        );
    }

    private FunctionVersionEnvVarResponse toEnvVarResponse(FunctionVersionEnvVar envVar) {
        return new FunctionVersionEnvVarResponse(
                envVar.getId(),
                envVar.getKey(),
                envVar.getValue(),
                envVar.getCreatedAt(),
                envVar.getUpdatedAt()
        );
    }

    private FunctionVersionSecretResponse toSecretResponse(FunctionVersionSecret secret) {
        return new FunctionVersionSecretResponse(
                secret.getId(),
                secret.getKey(),
                secret.getSecretRef(),
                secret.getCreatedAt(),
                secret.getUpdatedAt()
        );
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Config key is required");
        }
        if (key.length() > 255) {
            throw new IllegalArgumentException("Config key must be at most 255 characters");
        }
        if (!CONFIG_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Config key may only contain letters, numbers and '_' and must start with a letter or '_'");
        }
    }
}
