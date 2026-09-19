package com.funchole.backend.controlplane.dto;

import java.util.List;
import java.util.UUID;

public record FlowFullSourceResponse(
        UUID flowId,
        String flowKey,
        UUID flowVersionId,
        int version,
        String status,
        List<FlowStepSourceResponse> steps
) {
}
