package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FunctionVersionCreateRequest;
import com.funchole.backend.controlplane.dto.FunctionVersionResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.mapper.FunctionVersionMapper;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionCloneService;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.core.base.mapper.PaginationMapper;
import com.funchole.backend.core.base.response.ApiResponse;
import com.funchole.backend.core.base.response.PaginationResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions")
public class FunctionVersionController {
    private final FunctionVersionService functionVersionService;
    private final FunctionVersionCloneService functionVersionCloneService;
    private final FunctionVersionMapper functionVersionMapper;
    private final PaginationMapper paginationMapper;

    public FunctionVersionController(
            FunctionVersionService functionVersionService,
            FunctionVersionCloneService functionVersionCloneService,
            FunctionVersionMapper functionVersionMapper,
            PaginationMapper paginationMapper
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionCloneService = functionVersionCloneService;
        this.functionVersionMapper = functionVersionMapper;
        this.paginationMapper = paginationMapper;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<PaginationResponse<FunctionVersionResponse>> listVersions(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<FunctionVersionResponse> versions =
                functionVersionService.listVersions(appUserPrincipal.getId(), functionId, page, size)
                        .map(functionVersionMapper::toResponse);
        return ApiResponse.success(paginationMapper.toResponse(versions));
    }

    @GetMapping("/{versionId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionResponse> getVersionById(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);
        return ApiResponse.success(functionVersionMapper.toResponse(functionVersion));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionResponse> createDraftVersion(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @Valid @RequestBody FunctionVersionCreateRequest request
    ) {
        FunctionVersion functionVersion = functionVersionCloneService.createDraftVersion(appUserPrincipal.getId(), functionId, request);
        return ApiResponse.success(functionVersionMapper.toResponse(functionVersion));
    }
}
