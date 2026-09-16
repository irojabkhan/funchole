package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;

public record InvocationStepLogResponse(
        String stream,
        String message,
        OffsetDateTime createdAt
) {
}
