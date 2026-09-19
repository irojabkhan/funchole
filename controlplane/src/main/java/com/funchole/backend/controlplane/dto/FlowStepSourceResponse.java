package com.funchole.backend.controlplane.dto;

import java.util.UUID;

/**
 * Exactly one of {@code function}/{@code subFlow} is populated, matching
 * {@code componentType} (FUNCTION/RESPONSE/MIDDLEWARE vs SUB_FLOW) - unless
 * {@code unavailableReason} is set, in which case neither is: the referenced
 * Function/FunctionVersion/FlowVersion no longer exists, its source content
 * is gone from storage, or expanding it would revisit a FlowVersion already
 * on this path (a cycle).
 */
public record FlowStepSourceResponse(
        String stepKey,
        String componentType,
        int position,
        UUID componentId,
        UUID componentVersionId,
        FunctionVersionSourceDetailResponse function,
        FlowFullSourceResponse subFlow,
        String unavailableReason
) {
}
