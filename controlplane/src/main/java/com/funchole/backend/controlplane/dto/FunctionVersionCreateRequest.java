package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FunctionVersionCreateRequest(
        @Schema(defaultValue = "NODE", example = "NODE", description = "NODE (runs your handler code, for a "
                + "backend/API function) or STATIC (serves a pre-built static site's files directly, for a "
                + "frontend/UI) - defaults to the cloned version's runtime, or the parent Function's runtime if "
                + "there is nothing to clone, when omitted")
        @Nullable
        @Size(max = 100, message = "Runtime must be at most 100 characters")
        String runtime,

        @Schema(description = "Raw JSON text stored as-is alongside the version - never cloned from a prior "
                + "version, always exactly what's passed here", nullable = true)
        @Nullable
        String metadata,

        @Schema(description = "Clone this specific prior FunctionVersion's source/env vars/secrets/database "
                + "attachments into the new version instead of the Function's most recent version (must belong "
                + "to the same Function) - optional", nullable = true)
        @Nullable
        UUID cloneFromVersionId,

        @Schema(description = "true creates a genuinely empty DRAFT with no source/config, opting out of the "
                + "default auto-clone-from-latest-version behavior - optional, defaults to false", nullable = true)
        @Nullable
        Boolean startEmpty
) {
}
