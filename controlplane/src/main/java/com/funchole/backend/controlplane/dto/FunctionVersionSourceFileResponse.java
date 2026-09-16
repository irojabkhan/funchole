package com.funchole.backend.controlplane.dto;

public record FunctionVersionSourceFileResponse(
        String path,
        String content
) {
}
