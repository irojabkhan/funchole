package com.funchole.backend.invocationcontract;

import java.util.Optional;
import java.util.UUID;

/**
 * Explicit, transport-neutral boundary for reading back one exact
 * Invocation's durable state and step-level execution detail. Callers
 * (e.g. controlplane) depend only on this module - never on the Invocation
 * implementation module, the Dispatcher, or the Runtime Registry.
 *
 * <p>A single read, never a wait/poll/re-execution: {@link #inspect}
 * returns whatever is durably persisted right now, or {@link Optional#empty()}
 * if no such Invocation exists.
 */
public interface InvocationInspectionHandoff {

    Optional<InvocationInspectionResult> inspect(UUID invocationId);
}
