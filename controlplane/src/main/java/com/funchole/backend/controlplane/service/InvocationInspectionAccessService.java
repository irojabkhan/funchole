package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import com.funchole.backend.invocationcontract.InvocationInspectionHandoff;
import com.funchole.backend.invocationcontract.InvocationInspectionResult;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership-checked read access to one exact Invocation's durable state,
 * for a caller who knows its id (e.g. from a Gateway error response, or
 * from a prior direct-invocation dispatch). Depends only on the
 * {@code invocation-contract} module's {@link InvocationInspectionHandoff} -
 * never on the invocation implementation module, the Dispatcher, or the
 * Runtime Registry, matching {@link FunctionVersionInvocationService}'s own
 * boundary discipline.
 *
 * <p>An Invocation carries no owner column of its own - ownership is proven
 * transitively through whichever Flow or FunctionVersion it was created
 * from, exactly like every other ownership check in this service layer.
 * A caller who does not own that Flow/FunctionVersion gets the same
 * {@link ResourceNotFoundException} as a genuinely nonexistent invocation
 * id, never a distinguishable 403 - the ownership-scoped lookup pattern
 * already used throughout (e.g. {@code FlowService.getFlowById}).
 */
public class InvocationInspectionAccessService {

    private final InvocationInspectionHandoff invocationInspectionHandoff;
    private final FlowService flowService;
    private final FunctionVersionRepository functionVersionRepository;

    public InvocationInspectionAccessService(
            InvocationInspectionHandoff invocationInspectionHandoff,
            FlowService flowService,
            FunctionVersionRepository functionVersionRepository
    ) {
        this.invocationInspectionHandoff = invocationInspectionHandoff;
        this.flowService = flowService;
        this.functionVersionRepository = functionVersionRepository;
    }

    /**
     * Read-only, but still needs an open Hibernate session: the
     * DIRECT_FUNCTION branch below reads a lazily-fetched
     * {@code FunctionVersion.function} association, which throws
     * {@code LazyInitializationException} outside a transaction - a real
     * bug this fix closes, found live (masked in tests only by their own
     * {@code @Transactional}, which doesn't exist on a real HTTP request).
     */
    @Transactional(readOnly = true)
    public InvocationInspectionResult inspect(UUID appUserId, UUID invocationId) {
        InvocationInspectionResult inspection = invocationInspectionHandoff.inspect(invocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invocation not found: " + invocationId));

        if (inspection.flowId() != null) {
            // Ownership-scoped lookup - throws ResourceNotFoundException if this Flow isn't the caller's.
            flowService.getFlowById(appUserId, inspection.flowId());
            return inspection;
        }

        FunctionVersion functionVersion = functionVersionRepository.findById(inspection.functionVersionId())
                .filter(version -> version.getFunction().getDeletedAt() == null)
                .filter(version -> version.getFunction().getAppUser().getId().equals(appUserId))
                .orElseThrow(() -> new ResourceNotFoundException("Invocation not found: " + invocationId));
        return inspection;
    }
}
