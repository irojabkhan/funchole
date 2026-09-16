package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnvironmentProfileCreateRequest(
        @Schema(defaultValue = "production", example = "production")
        @NotBlank(message = "Environment key is required")
        @Size(max = 150, message = "Environment key must be at most 150 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Environment key may only contain letters, numbers, '_', '.' and '-'")
        String environmentKey,

        @Schema(defaultValue = "Production", example = "Production")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Schema(defaultValue = "Production credentials shared across flows", example = "Production credentials shared across flows")
        @Nullable
        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {
}
