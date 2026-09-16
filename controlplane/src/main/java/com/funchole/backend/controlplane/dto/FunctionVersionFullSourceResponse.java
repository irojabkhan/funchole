package com.funchole.backend.controlplane.dto;

import java.util.List;

public record FunctionVersionFullSourceResponse(
        String entrypoint,
        String handler,
        List<FunctionVersionSourceFileResponse> files
) {
}
