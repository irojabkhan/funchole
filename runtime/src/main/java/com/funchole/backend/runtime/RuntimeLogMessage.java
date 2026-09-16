package com.funchole.backend.runtime;

import java.util.UUID;

/**
 * Runtime Worker -> Dispatcher envelope. One line of newline-delimited JSON.
 * Zero or more may be sent for an executionId, at any point before its
 * terminal RESULT/ERROR - mirrors {@link NodeLogMessage} one hop further
 * down the IPC chain.
 */
record RuntimeLogMessage(
        String type,
        UUID executionId,
        String stream,
        String message
) {
    static final String TYPE = "LOG";

    static RuntimeLogMessage from(NodeLogMessage nodeLogMessage) {
        return new RuntimeLogMessage(TYPE, nodeLogMessage.executionId(), nodeLogMessage.stream(), nodeLogMessage.message());
    }
}
