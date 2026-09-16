package com.funchole.backend.dispatcher;

import java.util.UUID;

/**
 * One line of runtime console output, streamed asynchronously (possibly
 * several times) during an in-flight execution - unlike
 * {@link RuntimeExecutionResult}, never terminal and never awaited.
 */
public record RuntimeLogEntry(
        UUID executionId,
        String stream,
        String message
) {
}
