package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DatabaseResponse(
        UUID id,
        String name,
        String type,
        String host,
        int port,
        String databaseName,
        String username,
        boolean sslEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
