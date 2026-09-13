package com.funchole.backend.controlplane.dto;

import java.util.List;
import java.util.UUID;

public record FunctionVersionSourceResponse(
        UUID functionVersionId,
        String runtimeType,
        String runtimeVersion,
        String entrypoint,
        String handler,
        List<String> relativePaths
) {
}
