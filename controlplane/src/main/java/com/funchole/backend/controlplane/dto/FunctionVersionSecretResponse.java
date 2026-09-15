package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FunctionVersionSecretResponse(
        UUID id,
        String key,
        String secretRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
