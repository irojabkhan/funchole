package com.funchole.backend.invocation;

import com.funchole.backend.invocationcontract.FlowInvocationHandoff;
import com.funchole.backend.invocationcontract.FlowInvocationRequest;
import com.funchole.backend.invocationcontract.FlowInvocationResult;

/**
 * Default {@link FlowInvocationHandoff}: bridges a fully-resolved direct
 * FlowVersion execution request straight onto {@link InvocationRegistry#create},
 * reusing the existing durable Invocation creation, immutable execution
 * snapshot, PENDING state, and ready-event publication - the same
 * Dispatcher/Runtime execution path a real Gateway-routed HTTP request to
 * this Flow already goes through.
 */
public class InvocationRegistryFlowInvocationHandoff implements FlowInvocationHandoff {

    private final InvocationRegistry invocationRegistry;

    public InvocationRegistryFlowInvocationHandoff(InvocationRegistry invocationRegistry) {
        this.invocationRegistry = invocationRegistry;
    }

    @Override
    public FlowInvocationResult dispatch(FlowInvocationRequest request) {
        try {
            Invocation invocation = invocationRegistry.create(new CreateInvocationRequest(
                    request.flowId(), request.flowKey(), request.flowVersionId(), request.inputPayload()));
            return new FlowInvocationResult(
                    invocation.invocationId(),
                    invocation.flowVersionId(),
                    invocation.status().name()
            );
        } catch (RuntimeException exception) {
            throw new FlowInvocationDispatchException(
                    "Failed to dispatch direct flow invocation for flow version " + request.flowVersionId(), exception);
        }
    }
}
