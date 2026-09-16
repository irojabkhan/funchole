package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FunctionVersionDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionDatabaseService;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/databases")
public class FunctionVersionDatabaseController {

    private final FunctionVersionDatabaseService functionVersionDatabaseService;

    public FunctionVersionDatabaseController(FunctionVersionDatabaseService functionVersionDatabaseService) {
        this.functionVersionDatabaseService = functionVersionDatabaseService;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FunctionVersionDatabaseAttachmentResponse>> listAttachments(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        return ApiResponse.success(
                functionVersionDatabaseService.listAttachments(appUserPrincipal.getId(), functionId, versionId)
        );
    }

    @PutMapping("/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FunctionVersionDatabaseAttachmentResponse>> attachDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @PathVariable UUID databaseId
    ) {
        return ApiResponse.success(
                functionVersionDatabaseService.attachDatabase(appUserPrincipal.getId(), functionId, versionId, databaseId)
        );
    }

    @DeleteMapping("/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FunctionVersionDatabaseAttachmentResponse>> detachDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @PathVariable UUID databaseId
    ) {
        return ApiResponse.success(
                functionVersionDatabaseService.detachDatabase(appUserPrincipal.getId(), functionId, versionId, databaseId)
        );
    }
}
