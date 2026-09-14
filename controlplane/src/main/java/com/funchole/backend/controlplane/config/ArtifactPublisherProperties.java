package com.funchole.backend.controlplane.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.artifact")
public record ArtifactPublisherProperties(
        @NotNull URI endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        String region,
        boolean pathStyleAccess
) {
}
