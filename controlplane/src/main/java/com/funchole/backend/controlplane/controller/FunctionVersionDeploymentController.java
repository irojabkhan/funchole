package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FunctionVersionResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.mapper.FunctionVersionMapper;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionDeploymentService;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers the existing build-&gt;publish-&gt;finalize pipeline
 * ({@link FunctionVersionDeploymentService}) for a DRAFT FunctionVersion
 * that already has source submitted. Ownership is verified here (the
 * deployment service itself is transport-neutral and knows nothing about
 * AppUser); the pipeline's own lifecycle guards (DRAFT-only, no
 * republishing) are enforced inside the service and surface as 409 Conflict
 * via {@code core.GlobalExceptionHandler}, and a build failure surfaces as
 * 422 with stage/exit-code/stdout/stderr detail via {@link BuildExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/deploy")
public class FunctionVersionDeploymentController {
    private final FunctionVersionService functionVersionService;
    private final FunctionVersionDeploymentService functionVersionDeploymentService;
    private final FunctionVersionMapper functionVersionMapper;

    public FunctionVersionDeploymentController(
            FunctionVersionService functionVersionService,
            FunctionVersionDeploymentService functionVersionDeploymentService,
            FunctionVersionMapper functionVersionMapper
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionDeploymentService = functionVersionDeploymentService;
        this.functionVersionMapper = functionVersionMapper;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<FunctionVersionResponse> deploy(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);
        FunctionVersion deployed = functionVersionDeploymentService.deploy(versionId);
        return ApiResponse.success(functionVersionMapper.toResponse(deployed));
    }
}
