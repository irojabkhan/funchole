package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FunctionVersionConfigResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionEnvVarRequest;
import com.funchole.backend.controlplane.dto.FunctionVersionSecretRequest;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionConfigService;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/config")
public class FunctionVersionConfigController {

    private final FunctionVersionConfigService functionVersionConfigService;

    public FunctionVersionConfigController(FunctionVersionConfigService functionVersionConfigService) {
        this.functionVersionConfigService = functionVersionConfigService;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionConfigResponse> getConfig(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        return ApiResponse.success(functionVersionConfigService.getConfig(appUserPrincipal.getId(), functionId, versionId));
    }

    @PutMapping("/env/{key}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionConfigResponse> upsertEnvVar(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @PathVariable String key,
            @Valid @RequestBody FunctionVersionEnvVarRequest request
    ) {
        return ApiResponse.success(functionVersionConfigService.upsertEnvVar(
                appUserPrincipal.getId(),
                functionId,
                versionId,
                key,
                request.value()
        ));
    }

    @PutMapping("/secrets/{key}")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionConfigResponse> upsertSecret(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @PathVariable String key,
            @Valid @RequestBody FunctionVersionSecretRequest request
    ) {
        return ApiResponse.success(functionVersionConfigService.upsertSecret(
                appUserPrincipal.getId(),
                functionId,
                versionId,
                key,
                request.value()
        ));
    }
}
