package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank
        @Schema(description = "Signed Google ID token (JWT credential) obtained client-side via Google Identity Services")
        String idToken
) {
}
