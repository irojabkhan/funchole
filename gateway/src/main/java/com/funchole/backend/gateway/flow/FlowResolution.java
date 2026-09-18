package com.funchole.backend.gateway.flow;

import java.util.UUID;

/**
 * {@code routePrefix} is null for a literal exact-path Flow, and the
 * matched wildcard prefix (e.g. {@code "/app/"}) for one registered with a
 * trailing {@code /*} - see {@link PrefixRoute}. {@code staticFunctionVersionId}
 * is non-null only when this Flow's adopted version has a FUNCTION step
 * pointing at a {@code STATIC}-runtime FunctionVersion, in which case
 * {@code GatewayHttpHandler} serves files directly from that version's
 * artifact instead of dispatching an Invocation at all.
 */
public record FlowResolution(
        UUID flowId,
        String flowKey,
        UUID flowVersionId,
        String routePrefix,
        UUID staticFunctionVersionId
) {

    public FlowResolution(UUID flowId, String flowKey, UUID flowVersionId) {
        this(flowId, flowKey, flowVersionId, null, null);
    }
}
