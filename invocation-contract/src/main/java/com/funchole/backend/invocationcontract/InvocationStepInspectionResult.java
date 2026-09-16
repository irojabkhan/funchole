package com.funchole.backend.invocationcontract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transport-neutral, read-only view of one durable step execution, as
 * produced by an {@link InvocationInspectionHandoff}. Mirrors the dispatcher
 * module's own step execution record, but redeclared here so callers across
 * the boundary (controlplane) never compile against dispatcher-internal
 * types - only this contract module.
 */
public record InvocationStepInspectionResult(
        UUID stepId,
        int position,
        String componentType,
        UUID componentId,
        UUID componentVersionId,
        String status,
        int attempt,
        String result,
        String error,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        List<InvocationStepLogEntry> logs
) {
}
