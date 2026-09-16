package com.funchole.backend.dispatcher;

import java.util.UUID;

/**
 * Runtime Worker -> Dispatcher envelope. One line of newline-delimited JSON.
 * Zero or more may arrive for an executionId, at any point before its
 * terminal RESULT/ERROR.
 */
record IpcLogMessage(
        String type,
        UUID executionId,
        String stream,
        String message
) {
    static final String TYPE = "LOG";
}
