package com.funchole.backend.runtime;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Java Runtime Worker -> Node Executor handoff. Everything the artifact
 * needs is already here; the Node process never queries the FuncHole
 * database.
 */
public record NodeExecutionRequest(
        UUID executionId,
        UUID componentId,
        UUID componentVersionId,
        Path artifactPath,
        String handler,
        String input,
        Map<String, String> environment
) {
    public NodeExecutionRequest {
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public NodeExecutionRequest(
            UUID executionId,
            UUID componentId,
            UUID componentVersionId,
            Path artifactPath,
            String handler,
            String input
    ) {
        this(executionId, componentId, componentVersionId, artifactPath, handler, input, Map.of());
    }
}
