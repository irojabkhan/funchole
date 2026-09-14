package com.funchole.backend.invocation;

import java.util.UUID;

/**
 * {@code stepId} is execution-scoped, not always the originating
 * {@code FlowStep.id}: a step pulled into the snapshot from a nested
 * SUB_FLOW (see {@code JdbcInvocationRegistry}'s flattening) gets a freshly
 * synthesized id so two occurrences of the same sub-flow step can never
 * collide on {@code (invocation_id, step_id, attempt)}. {@code sourceStepId}
 * always points at the real originating {@code FlowStep.id}, for traceability.
 * For a step taken directly from the root flow (the common case), the two
 * are identical.
 */
public record InvocationStepSnapshot(
        UUID stepId,
        String stepKey,
        String componentType,
        int position,
        UUID componentId,
        UUID componentVersionId,
        String metadata,
        UUID sourceStepId
) {
}
