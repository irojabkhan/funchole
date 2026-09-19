package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

public record FunctionVersionCreateRequest(
        @Schema(defaultValue = "NODE", example = "NODE", description = "NODE (runs your handler code, for a "
                + "backend/API function) or STATIC (serves a pre-built static site's files directly, for a "
                + "frontend/UI) - defaults to the parent Function's runtime when omitted")
        @Nullable
        @Size(max = 100, message = "Runtime must be at most 100 characters")
        String runtime,

        @Schema(description = "Raw JSON text stored as-is alongside the version", nullable = true)
        @Nullable
        String metadata
) {
}
