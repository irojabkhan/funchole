package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FunctionVersionEnvVarResponse(
        UUID id,
        String key,
        String value,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
