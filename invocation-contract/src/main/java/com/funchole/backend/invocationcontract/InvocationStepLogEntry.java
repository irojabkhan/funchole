package com.funchole.backend.invocationcontract;

import java.time.OffsetDateTime;

/**
 * One line of durable runtime console output ({@code console.log}/
 * {@code console.error}) captured during a step's execution. Transport-neutral
 * mirror of the dispatcher module's own log record, redeclared here so
 * callers across the boundary (controlplane) never compile against
 * dispatcher-internal types.
 */
public record InvocationStepLogEntry(
        String stream,
        String message,
        OffsetDateTime createdAt
) {
}
