package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @Schema(defaultValue = "My coding agent", example = "My coding agent")
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name
) {
}
