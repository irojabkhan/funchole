package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnvironmentProfileResponse(
        UUID id,
        String environmentKey,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
