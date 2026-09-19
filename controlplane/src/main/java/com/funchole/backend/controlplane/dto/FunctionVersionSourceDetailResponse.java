package com.funchole.backend.controlplane.dto;

import java.util.List;
import java.util.UUID;

public record FunctionVersionSourceDetailResponse(
        UUID functionId,
        String functionKey,
        UUID functionVersionId,
        int version,
        String status,
        String runtime,
        String entrypoint,
        String handler,
        List<FunctionVersionSourceFileResponse> files
) {
}
