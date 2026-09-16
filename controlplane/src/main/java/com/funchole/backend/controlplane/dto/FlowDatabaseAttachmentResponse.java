package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FlowDatabaseAttachmentResponse(
        UUID id,
        UUID databaseId,
        String databaseName,
        String databaseType,
        OffsetDateTime createdAt
) {
}
