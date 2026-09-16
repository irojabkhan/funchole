package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FlowEnvironmentAttachmentResponse(
        UUID id,
        UUID environmentProfileId,
        String environmentKey,
        String environmentName,
        int priority,
        OffsetDateTime createdAt
) {
}
