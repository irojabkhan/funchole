package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Returned only from the create endpoint - {@code rawKey} is shown exactly
 * once and never retrievable again afterward.
 */
public record ApiKeyCreateResponse(
        UUID id,
        String name,
        String keyPrefix,
        String rawKey,
        OffsetDateTime createdAt
) {
}
