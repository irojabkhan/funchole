package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

public record FlowEnvironmentAttachmentRequest(
        @Schema(defaultValue = "100", example = "100")
        @Nullable
        Integer priority
) {
}
