package com.funchole.backend.controlplane.dto;

import java.time.OffsetDateTime;

public record FunctionVersionBuildLogResponse(
        String stage,
        String command,
        Integer exitCode,
        boolean succeeded,
        boolean timedOut,
        String stdout,
        String stderr,
        OffsetDateTime createdAt
) {
}
