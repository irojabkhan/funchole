package com.funchole.backend.runtime;

import java.util.UUID;

record NodeLogMessage(
        String type,
        UUID executionId,
        String stream,
        String message
) {
    static final String TYPE = "LOG";
}
