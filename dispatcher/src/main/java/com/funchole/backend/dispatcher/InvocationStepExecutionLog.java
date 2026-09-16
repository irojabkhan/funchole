package com.funchole.backend.dispatcher;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvocationStepExecutionLog(
        UUID id,
        UUID invocationStepExecutionId,
        String stream,
        String message,
        OffsetDateTime createdAt
) {
}
