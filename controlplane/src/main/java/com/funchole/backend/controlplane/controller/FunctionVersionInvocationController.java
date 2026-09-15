package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.DirectInvocationResponse;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.DirectFunctionInvocationCommand;
import com.funchole.backend.controlplane.service.FunctionVersionInvocationService;
import com.funchole.backend.controlplane.service.FunctionVersionService;
import com.funchole.backend.core.base.response.ApiResponse;
import com.funchole.backend.invocationcontract.DirectInvocationResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers a direct FunctionVersion invocation - no Flow, Gateway, or Route -
 * through the already-tested {@link FunctionVersionInvocationService}.
 * Ownership is verified here (the service itself is transport-neutral); the
 * READY-only guard surfaces as 409 Conflict via {@code core.GlobalExceptionHandler}'s
 * existing {@code IllegalStateException} mapping.
 *
 * <p>The request body, if present, is passed through verbatim as the raw
 * JSON text of the invocation's input payload - there is no wrapper field,
 * so a caller (e.g. the Function workspace's "Test" panel) can POST
 * whatever JSON value they want the function to receive.
 */
@RestController
@RequestMapping("/api/v1/functions/{functionId}/versions/{versionId}/invoke")
public class FunctionVersionInvocationController {

    private final FunctionVersionService functionVersionService;
    private final FunctionVersionInvocationService functionVersionInvocationService;

    public FunctionVersionInvocationController(
            FunctionVersionService functionVersionService,
            FunctionVersionInvocationService functionVersionInvocationService
    ) {
        this.functionVersionService = functionVersionService;
        this.functionVersionInvocationService = functionVersionInvocationService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DirectInvocationResponse> invoke(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID functionId,
            @PathVariable UUID versionId,
            @RequestBody(required = false) @Nullable String inputPayload
    ) {
        functionVersionService.getVersionById(appUserPrincipal.getId(), functionId, versionId);

        DirectInvocationResult result = functionVersionInvocationService.invoke(
                new DirectFunctionInvocationCommand(versionId, normalizePayload(inputPayload)));

        return ApiResponse.success(new DirectInvocationResponse(
                result.invocationId(), result.functionVersionId(), result.initialStatus()));
    }

    private String normalizePayload(@Nullable String inputPayload) {
        return inputPayload == null || inputPayload.isBlank() ? "{}" : inputPayload;
    }
}
