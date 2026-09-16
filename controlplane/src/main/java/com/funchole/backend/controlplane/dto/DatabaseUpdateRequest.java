package com.funchole.backend.controlplane.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DatabaseUpdateRequest(
        @Schema(defaultValue = "primary", example = "primary")
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @Schema(defaultValue = "db.example.com", example = "db.example.com")
        @NotBlank(message = "Host is required")
        @Size(max = 255, message = "Host must be at most 255 characters")
        String host,

        @Schema(defaultValue = "5432", example = "5432")
        @NotNull(message = "Port is required")
        @Min(value = 1, message = "Port must be at least 1")
        @Max(value = 65535, message = "Port must be at most 65535")
        Integer port,

        @Schema(defaultValue = "postgres", example = "postgres")
        @NotBlank(message = "Database name is required")
        @Size(max = 255, message = "Database name must be at most 255 characters")
        String databaseName,

        @Schema(defaultValue = "postgres", example = "postgres")
        @NotBlank(message = "Username is required")
        @Size(max = 255, message = "Username must be at most 255 characters")
        String username,

        @Schema(defaultValue = "changeme", example = "changeme")
        @Nullable
        String password,

        @Schema(defaultValue = "true", example = "true")
        @Nullable
        Boolean sslEnabled
) {
}
