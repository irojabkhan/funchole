package com.funchole.backend.controlplane.service;

import java.util.UUID;

/**
 * Caller-facing input for a direct FlowVersion invocation. Callers supply
 * ONLY the exact flowVersionId and the invocation input - flow identity is
 * resolved from the durable FlowVersion and can never be overridden from
 * outside.
 */
public record DirectFlowInvocationCommand(
        UUID flowVersionId,
        String inputPayload
) {
}
