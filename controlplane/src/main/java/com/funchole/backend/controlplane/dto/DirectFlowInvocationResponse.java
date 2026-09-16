package com.funchole.backend.controlplane.dto;

import java.util.UUID;

public record DirectFlowInvocationResponse(
        UUID invocationId,
        UUID flowVersionId,
        String initialStatus
) {
}
