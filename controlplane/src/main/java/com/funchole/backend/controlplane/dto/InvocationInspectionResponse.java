package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Exactly one of the two identity groups is populated, depending on how the
 * invocation was created: {@code flowId}/{@code flowKey}/{@code flowVersionId}
 * for a normal Flow invocation, or {@code functionVersionId} for a direct
 * FunctionVersion invocation.
 */
public record InvocationInspectionResponse(
        UUID invocationId,
        String status,
        UUID flowId,
        String flowKey,
        UUID flowVersionId,
        UUID functionVersionId,
        String inputPayload,
        String result,
        String error,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        List<InvocationStepInspectionResponse> steps
) {
}
