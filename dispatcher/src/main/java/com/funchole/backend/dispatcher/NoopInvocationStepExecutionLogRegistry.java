package com.funchole.backend.dispatcher;

import java.util.List;
import java.util.UUID;

public final class NoopInvocationStepExecutionLogRegistry implements InvocationStepExecutionLogRegistry {

    @Override
    public void append(UUID invocationStepExecutionId, String stream, String message) {
        // No-op: used by constructor overloads that don't wire durable log storage.
    }

    @Override
    public List<InvocationStepExecutionLog> findAllByStepExecutionId(UUID invocationStepExecutionId) {
        return List.of();
    }
}
