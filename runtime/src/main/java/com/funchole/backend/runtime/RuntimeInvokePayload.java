package com.funchole.backend.runtime;

import java.util.UUID;
import java.util.Map;

/**
 * Wire shape of the INVOKE payload received from the Dispatcher over IPC.
 * This is the complete handoff contract - the worker never re-queries the
 * FuncHole database for any of this data.
 */
record RuntimeInvokePayload(
        UUID invocationId,
        UUID flowId,
        UUID flowVersionId,
        UUID stepId,
        int attempt,
        String componentType,
        UUID componentId,
        UUID componentVersionId,
        String runtimeType,
        String input,
        Map<String, String> environment
) {
    public RuntimeInvokePayload {
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}
