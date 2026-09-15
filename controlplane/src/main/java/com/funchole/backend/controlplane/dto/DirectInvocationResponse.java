package com.funchole.backend.controlplane.dto;

import java.util.UUID;

public record DirectInvocationResponse(
        UUID invocationId,
        UUID functionVersionId,
        String initialStatus
) {
}
