package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FunctionVersionEnvVarRequest(
        @Schema(defaultValue = "production", example = "production")
        @NotNull(message = "Value is required")
        String value
) {
}
