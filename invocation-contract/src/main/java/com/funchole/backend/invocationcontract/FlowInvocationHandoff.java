package com.funchole.backend.invocationcontract;

/**
 * Explicit, transport-neutral boundary for handing a fully-resolved,
 * exactly-pinned direct FlowVersion execution request to the Invocation
 * subsystem. Callers (e.g. controlplane) depend only on this module - never
 * on the Invocation implementation module, the Dispatcher, or the Runtime
 * Registry.
 *
 * <p>The Invocation subsystem owns everything past this point: durable
 * Invocation creation (FLOW kind), the immutable execution snapshot built
 * from this exact FlowVersion's own steps, the initial PENDING state, and
 * ready-event publication onto the existing Dispatcher/Runtime execution
 * path - the same path a real Gateway-routed HTTP request to this Flow
 * already goes through. No second execution engine, and no Gateway/HTTP/TLS
 * hop.
 */
public interface FlowInvocationHandoff {

    FlowInvocationResult dispatch(FlowInvocationRequest request);

    /** Boundary-facing failure: dispatch could not create/queue the invocation. */
    class FlowInvocationDispatchException extends RuntimeException {
        public FlowInvocationDispatchException(String message, Throwable cause) {
            super(message, cause);
        }

        public FlowInvocationDispatchException(String message) {
            super(message);
        }
    }
}
