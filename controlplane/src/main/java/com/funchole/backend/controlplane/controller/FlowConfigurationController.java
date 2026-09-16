package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FlowDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.dto.FlowEnvironmentAttachmentRequest;
import com.funchole.backend.controlplane.dto.FlowEnvironmentAttachmentResponse;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FlowConfigurationService;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flows/{flowId}")
public class FlowConfigurationController {

    private final FlowConfigurationService flowConfigurationService;

    public FlowConfigurationController(FlowConfigurationService flowConfigurationService) {
        this.flowConfigurationService = flowConfigurationService;
    }

    @GetMapping("/environments")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowEnvironmentAttachmentResponse>> listEnvironmentAttachments(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId
    ) {
        return ApiResponse.success(flowConfigurationService.listEnvironmentAttachments(appUserPrincipal.getId(), flowId));
    }

    @PutMapping("/environments/{environmentId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowEnvironmentAttachmentResponse>> attachEnvironment(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId,
            @PathVariable UUID environmentId,
            @Valid @RequestBody(required = false) FlowEnvironmentAttachmentRequest request
    ) {
        Integer priority = request == null ? null : request.priority();
        return ApiResponse.success(flowConfigurationService.attachEnvironment(
                appUserPrincipal.getId(),
                flowId,
                environmentId,
                priority
        ));
    }

    @DeleteMapping("/environments/{environmentId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowEnvironmentAttachmentResponse>> detachEnvironment(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId,
            @PathVariable UUID environmentId
    ) {
        return ApiResponse.success(flowConfigurationService.detachEnvironment(
                appUserPrincipal.getId(),
                flowId,
                environmentId
        ));
    }

    @GetMapping("/databases")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowDatabaseAttachmentResponse>> listDatabaseAttachments(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId
    ) {
        return ApiResponse.success(flowConfigurationService.listDatabaseAttachments(appUserPrincipal.getId(), flowId));
    }

    @PutMapping("/databases/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowDatabaseAttachmentResponse>> attachDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId,
            @PathVariable UUID databaseId
    ) {
        return ApiResponse.success(flowConfigurationService.attachDatabase(appUserPrincipal.getId(), flowId, databaseId));
    }

    @DeleteMapping("/databases/{databaseId}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FlowDatabaseAttachmentResponse>> detachDatabase(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId,
            @PathVariable UUID databaseId
    ) {
        return ApiResponse.success(flowConfigurationService.detachDatabase(appUserPrincipal.getId(), flowId, databaseId));
    }
}
