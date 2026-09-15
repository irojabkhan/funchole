package com.funchole.backend.controlplane.dto;

import java.util.List;
import java.util.UUID;

public record FunctionVersionConfigResponse(
        UUID functionVersionId,
        List<FunctionVersionEnvVarResponse> envVars,
        List<FunctionVersionSecretResponse> secrets
) {
}
