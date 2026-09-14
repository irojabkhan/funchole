package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvocationStepInspectionResponse(
        UUID stepId,
        int position,
        String componentType,
        UUID componentId,
        UUID componentVersionId,
        String status,
        int attempt,
        String result,
        String error,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
