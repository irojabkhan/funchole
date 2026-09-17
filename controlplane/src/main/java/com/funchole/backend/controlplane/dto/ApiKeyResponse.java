package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt
) {
}
