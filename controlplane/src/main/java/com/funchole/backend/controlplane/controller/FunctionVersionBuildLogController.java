package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.FunctionVersionBuildLogResponse;
import com.funchole.backend.controlplane.entity.FunctionVersionBuildLog;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.FunctionVersionBuildLogService;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.core.base.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to a FunctionVersion's persisted build-stage diagnostics
 * (F248/F250) - every {@code npm ci/install}/{@code npm run build} stage a
 * deploy attempt ran, in order, whether it succeeded or failed, still
 * inspectable long after the deploy request/response that triggered it is
 * gone. Empty (not 404) for a version that was never deployed, or whose
 * runtime never runs any build stage (e.g. a NODE version with no
 * {@code package.json}).
 */
@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/build-logs")
public class FunctionVersionBuildLogController {

    private final FunctionVersionService functionVersionService;
    private final FunctionVersionBuildLogService buildLogService;

    public FunctionVersionBuildLogController(
            FunctionVersionService functionVersionService,
            FunctionVersionBuildLogService buildLogService
    ) {
        this.functionVersionService = functionVersionService;
        this.buildLogService = buildLogService;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<FunctionVersionBuildLogResponse>> listBuildLogs(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId
    ) {
        functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);
        List<FunctionVersionBuildLogResponse> logs = buildLogService.listLogs(versionId).stream()
                .map(FunctionVersionBuildLogController::toResponse)
                .toList();
        return ApiResponse.success(logs);
    }

    private static FunctionVersionBuildLogResponse toResponse(FunctionVersionBuildLog log) {
        return new FunctionVersionBuildLogResponse(
                log.getStage(), log.getCommand(), log.getExitCode(), log.isSucceeded(), log.isTimedOut(),
                log.getStdout(), log.getStderr(), log.getCreatedAt());
    }
}
