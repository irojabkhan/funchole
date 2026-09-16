package com.funchole.backend.dispatcher;

import java.util.List;
import java.util.UUID;

/**
 * Durable store for the runtime console output ({@code console.log}/
 * {@code console.error}) of a step execution, captured as it streams in
 * during execution (see {@link IpcRuntimeExecutionGateway}) - not read back
 * from a terminal RESULT/ERROR, since those never carry it.
 */
public interface InvocationStepExecutionLogRegistry {

    void append(UUID invocationStepExecutionId, String stream, String message);

    List<InvocationStepExecutionLog> findAllByStepExecutionId(UUID invocationStepExecutionId);
}
