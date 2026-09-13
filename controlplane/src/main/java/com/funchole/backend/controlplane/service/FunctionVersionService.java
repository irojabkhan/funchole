package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FunctionVersionCreateRequest;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create/read/list for FunctionVersion. Status transitions (DRAFT -&gt;
 * PUBLISHING -&gt; READY/FAILED) remain owned by {@link FunctionVersionLifecycleRegistry}
 * and are not duplicated here - this service only ever creates a new
 * version in DRAFT.
 */
@Service
public class FunctionVersionService {

    private final FunctionVersionRepository functionVersionRepository;
    private final FunctionService functionService;

    public FunctionVersionService(FunctionVersionRepository functionVersionRepository, FunctionService functionService) {
        this.functionVersionRepository = functionVersionRepository;
        this.functionService = functionService;
    }

    public Page<FunctionVersion> listVersions(UUID appUserId, UUID functionId, int page, int size) {
        functionService.getFunctionById(appUserId, functionId);
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, FunctionVersion::getVersion)
        );
        return functionVersionRepository.findAllByFunction_Id(functionId, pageable);
    }

    public FunctionVersion getVersionById(UUID appUserId, UUID functionId, UUID versionId) {
        functionService.getFunctionById(appUserId, functionId);
        return functionVersionRepository.findByIdAndFunction_Id(versionId, functionId)
                .orElseThrow(() -> new ResourceNotFoundException("Function version not found: " + versionId));
    }

    @Transactional
    public FunctionVersion createDraftVersion(UUID appUserId, UUID functionId, FunctionVersionCreateRequest request) {
        Function function = functionService.getFunctionById(appUserId, functionId);
        int nextVersion = functionVersionRepository.findMaxVersion(functionId) + 1;
        String runtime = request.runtime() != null ? request.runtime() : function.getRuntime();

        FunctionVersion functionVersion = FunctionVersion.create(function, nextVersion, runtime, request.metadata());
        return functionVersionRepository.save(functionVersion);
    }
}
