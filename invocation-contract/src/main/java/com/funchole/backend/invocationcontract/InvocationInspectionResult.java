package com.funchole.backend.invocationcontract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transport-neutral, read-only view of one exact Invocation's durable state
 * plus its step-level execution detail, as produced by an
 * {@link InvocationInspectionHandoff}. Never the persisted Invocation record
 * itself - only the fields a caller across the boundary needs.
 *
 * <p>Exactly one of the two identity groups below is populated, depending on
 * how the invocation was created:
 * <ul>
 *   <li>{@code flowId}/{@code flowKey}/{@code flowVersionId} - a normal Flow
 *       invocation.</li>
 *   <li>{@code functionVersionId} - a direct FunctionVersion invocation.</li>
 * </ul>
 *
 * <p>{@code steps} is always empty for a direct FunctionVersion invocation
 * (there is no step chain to report) and may be empty for a Flow invocation
 * that has not yet dispatched its first step.
 */
public record InvocationInspectionResult(
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
        List<InvocationStepInspectionResult> steps
) {
}
