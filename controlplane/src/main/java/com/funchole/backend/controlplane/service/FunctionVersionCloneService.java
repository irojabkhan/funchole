package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FunctionVersionCreateRequest;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps {@link FunctionVersionService#createDraftVersion} so a new
 * FunctionVersion starts as a clone of the Function's most recent version
 * (source, env vars, secrets, database attachments) by default, rather than
 * empty - a coding agent iterating after a FAILED build should be fixing
 * forward, not regenerating everything from scratch. Callers can opt out
 * ({@code startEmpty}) or target a specific prior version
 * ({@code cloneFromVersionId}) instead of the latest.
 *
 * <p>Lives above {@link FunctionVersionService} rather than inside it:
 * {@link FunctionVersionConfigService} and {@link FunctionVersionDatabaseService}
 * already depend on {@link FunctionVersionService} (for its ownership-checked
 * {@code getVersionById}), so folding this orchestration into
 * {@link FunctionVersionService} itself would create a circular dependency.
 */
@Service
public class FunctionVersionCloneService {

    private final FunctionVersionService functionVersionService;
    private final FunctionVersionRepository functionVersionRepository;
    private final FunctionVersionSourceService sourceService;
    private final FunctionVersionConfigService configService;
    private final FunctionVersionDatabaseService databaseService;

    public FunctionVersionCloneService(
            FunctionVersionService functionVersionService,
            FunctionVersionRepository functionVersionRepository,
            FunctionVersionSourceService sourceService,
            FunctionVersionConfigService configService,
            FunctionVersionDatabaseService databaseService
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionRepository = functionVersionRepository;
        this.sourceService = sourceService;
        this.configService = configService;
        this.databaseService = databaseService;
    }

    @Transactional
    public FunctionVersion createDraftVersion(UUID appUserId, UUID functionId, FunctionVersionCreateRequest request) {
        FunctionVersion cloneSource = resolveCloneSource(appUserId, functionId, request);
        // A cloned version defaults to the source version's own runtime (a
        // faithful copy), not the Function's own runtime - only relevant
        // when the caller didn't explicitly ask for a different runtime.
        String effectiveRuntime = request.runtime() != null
                ? request.runtime()
                : (cloneSource != null ? cloneSource.getRuntime() : null);

        FunctionVersion created = functionVersionService.createDraftVersion(
                appUserId, functionId,
                new FunctionVersionCreateRequest(effectiveRuntime, request.metadata(), null, null));

        if (cloneSource != null) {
            sourceService.findSource(cloneSource.getId())
                    .ifPresent(bundle -> sourceService.submitSource(created.getId(), bundle));
            configService.cloneConfig(cloneSource, created);
            databaseService.cloneAttachments(cloneSource, created);
        }
        return created;
    }

    private FunctionVersion resolveCloneSource(UUID appUserId, UUID functionId, FunctionVersionCreateRequest request) {
        if (Boolean.TRUE.equals(request.startEmpty())) {
            return null;
        }
        if (request.cloneFromVersionId() != null) {
            // getVersionById already enforces ownership and that the version
            // belongs to this exact Function.
            return functionVersionService.getVersionById(appUserId, functionId, request.cloneFromVersionId());
        }
        return functionVersionRepository.findFirstByFunction_IdOrderByVersionDesc(functionId).orElse(null);
    }
}
