package com.funchole.backend.controlplane.controller;

import com.funchole.backend.controlplane.dto.DirectFlowInvocationResponse;
import com.funchole.backend.controlplane.security.AppUserPrincipal;
import com.funchole.backend.controlplane.service.DirectFlowInvocationCommand;
import com.funchole.backend.controlplane.service.FlowVersionInvocationService;
import com.funchole.backend.controlplane.service.FlowVersionService;
import com.funchole.backend.core.base.response.ApiResponse;
import com.funchole.backend.invocationcontract.FlowInvocationResult;
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
 * Triggers a direct FlowVersion invocation - no HTTP route, Gateway, or TLS -
 * through the already-tested {@link FlowVersionInvocationService}. Ownership
 * is verified here (the service itself is transport-neutral); the
 * ARCHIVED-only guard surfaces as 409 Conflict via {@code core.GlobalExceptionHandler}'s
 * existing {@code IllegalStateException} mapping.
 *
 * <p>The request body, if present, is passed through verbatim as the raw
 * JSON text of the invocation's input payload - there is no wrapper field,
 * so a caller (e.g. the Flow builder's "Test flow" panel) can POST whatever
 * JSON value they want the flow's first step to receive.
 */
@RestController
@RequestMapping("/api/v1/flows/{flowId}/versions/{versionId}/invoke")
public class FlowVersionInvocationController {

    private final FlowVersionService flowVersionService;
    private final FlowVersionInvocationService flowVersionInvocationService;

    public FlowVersionInvocationController(
            FlowVersionService flowVersionService,
            FlowVersionInvocationService flowVersionInvocationService
    ) {
        this.flowVersionService = flowVersionService;
        this.flowVersionInvocationService = flowVersionInvocationService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<DirectFlowInvocationResponse> invoke(
            @AuthenticationPrincipal AppUserPrincipal appUserPrincipal,
            @PathVariable UUID flowId,
            @PathVariable UUID versionId,
            @RequestBody(required = false) @Nullable String inputPayload
    ) {
        flowVersionService.getVersionById(appUserPrincipal.getId(), flowId, versionId);

        FlowInvocationResult result = flowVersionInvocationService.invoke(
                new DirectFlowInvocationCommand(versionId, normalizePayload(inputPayload)));

        return ApiResponse.success(new DirectFlowInvocationResponse(
                result.invocationId(), result.flowVersionId(), result.initialStatus()));
    }

    private String normalizePayload(@Nullable String inputPayload) {
        return inputPayload == null || inputPayload.isBlank() ? "{}" : inputPayload;
    }
}
