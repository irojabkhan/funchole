package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.constant.FlowVersionStatus;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.repository.FlowVersionRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import com.funchole.backend.invocationcontract.FlowInvocationHandoff;
import com.funchole.backend.invocationcontract.FlowInvocationRequest;
import com.funchole.backend.invocationcontract.FlowInvocationResult;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transport-neutral application service for directly invoking a FlowVersion
 * without a Gateway, HTTP route, or TLS - the exact same durable Invocation
 * creation, snapshot, and Dispatcher/Runtime execution path a real
 * Gateway-routed HTTP request to this Flow would go through, just triggered
 * from Controlplane instead.
 *
 * <pre>
 * caller command (flowVersionId + payload only)
 * -&gt; status guard (this class - DRAFT and ADOPTED allowed, ARCHIVED rejected)
 * -&gt; durable FlowVersion resolution -&gt; pinned request (flow identity from durable state)
 * -&gt; exactly-pinned invocation hand-off (the invocation-contract module's boundary types)
 * -&gt; existing invocation / dispatcher / runtime path
 * </pre>
 *
 * <p>Unlike direct FunctionVersion invocation (which requires READY), a
 * DRAFT FlowVersion may be tested here too: {@code JdbcInvocationRegistry#create}
 * has no status restriction of its own, and structural validation (a
 * terminal RESPONSE step, resolvable component references, etc.) already
 * happens asynchronously at the Dispatcher regardless of how the invocation
 * was triggered - so testing a Flow before adoption surfaces the same
 * failures a caller would eventually hit, without requiring adoption first.
 * Only ARCHIVED (explicitly retired) versions are rejected.
 *
 * <p>This class depends only on the {@code invocation-contract} module's
 * {@link FlowInvocationHandoff}. It never imports (and cannot - they are not
 * even on this module's compile classpath) {@code InvocationRegistry},
 * {@code JdbcInvocationRegistry}, the persisted {@code Invocation} record,
 * the Dispatcher, or the Runtime Registry.
 */
public class FlowVersionInvocationService {

    private final FlowVersionRepository flowVersionRepository;
    private final FlowInvocationHandoff invocationHandoff;

    public FlowVersionInvocationService(
            FlowVersionRepository flowVersionRepository,
            FlowInvocationHandoff invocationHandoff
    ) {
        this.flowVersionRepository = flowVersionRepository;
        this.invocationHandoff = invocationHandoff;
    }

    @Transactional(readOnly = true)
    public FlowInvocationResult invoke(DirectFlowInvocationCommand command) {
        if (command == null || command.flowVersionId() == null) {
            throw new IllegalArgumentException("flowVersionId is required");
        }
        FlowVersion flowVersion = flowVersionRepository.findById(command.flowVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Flow version not found: " + command.flowVersionId()));
        if (flowVersion.getStatus() == FlowVersionStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "Cannot invoke an ARCHIVED flow version: " + command.flowVersionId());
        }

        // The exact FlowVersion is the pin: flow identity and version id are
        // copied from the durable FlowVersion/Flow themselves - never
        // resolved from an active/latest version, and never taken from
        // caller input.
        FlowInvocationRequest request = new FlowInvocationRequest(
                flowVersion.getFlow().getId(),
                flowVersion.getFlow().getFlowKey(),
                flowVersion.getId(),
                command.inputPayload()
        );
        return invocationHandoff.dispatch(request);
    }
}
