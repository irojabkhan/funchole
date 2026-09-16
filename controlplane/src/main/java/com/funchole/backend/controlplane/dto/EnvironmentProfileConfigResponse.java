package com.funchole.backend.controlplane.dto;

import java.util.List;
import java.util.UUID;

public record EnvironmentProfileConfigResponse(
        UUID environmentProfileId,
        List<FunctionVersionEnvVarResponse> envVars,
        List<FunctionVersionSecretResponse> secrets
) {
}
