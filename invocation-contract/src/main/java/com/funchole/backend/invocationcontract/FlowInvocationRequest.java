package com.funchole.backend.invocationcontract;

import java.util.UUID;

/**
 * A fully-resolved, exactly-pinned request to trigger one FlowVersion's own
 * step snapshot directly - no Gateway, no HTTP route, no TLS. Produces the
 * exact same kind of Invocation and execution path a real Gateway-routed
 * HTTP request to this Flow would produce.
 */
public record FlowInvocationRequest(
        UUID flowId,
        String flowKey,
        UUID flowVersionId,
        String inputPayload
) {
}
