package com.funchole.backend.controlplane.dto;

import com.funchole.backend.controlplane.constant.FunctionVersionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FunctionVersionResponse(
        UUID id,
        UUID functionId,
        int version,
        FunctionVersionStatus status,
        String runtime,
        String artifactObjectKey,
        String artifactFormat,
        String artifactSha256,
        Long artifactSizeBytes,
        OffsetDateTime artifactPublishedAt,
        String metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
