package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FunctionVersionSecretRequest(
        @Schema(defaultValue = "super-secret-value", example = "super-secret-value")
        @NotNull(message = "Value is required")
        String value
) {
}
