package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.constant.FlowStepComponentType;
import com.funchole.backend.controlplane.dto.FlowFullSourceResponse;
import com.funchole.backend.controlplane.dto.FlowStepSourceResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceDetailResponse;
import com.funchole.backend.controlplane.dto.FunctionVersionSourceFileResponse;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FlowStep;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.entity.SourceBundle;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a Flow's entire dependency tree - every step's Function source,
 * recursing into SUB_FLOW steps - in one read, so a caller (an MCP tool, an
 * agent) never has to walk the graph itself one call at a time.
 *
 * <p>Read-only: every lookup reuses the same ownership-checked service
 * methods the individual Flow/FlowVersion/FunctionVersion tools already use,
 * so this can never expose a resource the caller doesn't own.
 *
 * <p>A step whose referenced Function/FunctionVersion/FlowVersion no longer
 * exists, whose source content is gone from storage (see
 * {@link LocalSourceStore}), or whose SUB_FLOW would revisit a FlowVersion
 * already on the current path (a cycle - {@link FlowStepReferenceValidator}
 * does not itself reject one) is reported via {@code unavailableReason}
 * rather than failing the whole call, so a broken corner of the graph never
 * hides the rest of it.
 */
@Service
public class FlowFullSourceService {

    private final FlowVersionService flowVersionService;
    private final FlowStepService flowStepService;
    private final FunctionService functionService;
    private final FunctionVersionService functionVersionService;
    private final FunctionVersionSourceService functionVersionSourceService;

    public FlowFullSourceService(
            FlowVersionService flowVersionService,
            FlowStepService flowStepService,
            FunctionService functionService,
            FunctionVersionService functionVersionService,
            FunctionVersionSourceService functionVersionSourceService
    ) {
        this.flowVersionService = flowVersionService;
        this.flowStepService = flowStepService;
        this.functionService = functionService;
        this.functionVersionService = functionVersionService;
        this.functionVersionSourceService = functionVersionSourceService;
    }

    /**
     * Transactional (unlike the read methods it composes): the traversal
     * dereferences lazy associations (e.g. {@code FlowVersion.getFlow()})
     * across several repository calls, which needs one persistence context
     * held open for the whole walk rather than the short-lived one each
     * individual repository call would otherwise get on its own.
     */
    @Transactional(readOnly = true)
    public FlowFullSourceResponse getFullSource(UUID appUserId, UUID flowId, UUID versionId) {
        Set<UUID> visitedFlowVersionIds = new LinkedHashSet<>();
        visitedFlowVersionIds.add(versionId);
        return resolveFlow(appUserId, flowId, versionId, visitedFlowVersionIds);
    }

    private FlowFullSourceResponse resolveFlow(UUID appUserId, UUID flowId, UUID versionId, Set<UUID> visitedFlowVersionIds) {
        FlowVersion flowVersion = flowVersionService.getVersionById(appUserId, flowId, versionId);
        List<FlowStep> steps = flowStepService.listSteps(appUserId, flowId, versionId);
        List<FlowStepSourceResponse> stepResponses = steps.stream()
                .map(step -> resolveStep(appUserId, step, visitedFlowVersionIds))
                .toList();
        return new FlowFullSourceResponse(
                flowId,
                flowVersion.getFlow().getFlowKey(),
                versionId,
                flowVersion.getVersion(),
                flowVersion.getStatus().name(),
                stepResponses
        );
    }

    private FlowStepSourceResponse resolveStep(UUID appUserId, FlowStep step, Set<UUID> visitedFlowVersionIds) {
        if (step.getComponentType() == FlowStepComponentType.SUB_FLOW) {
            return resolveSubFlowStep(appUserId, step, visitedFlowVersionIds);
        }
        return resolveFunctionStep(appUserId, step);
    }

    private FlowStepSourceResponse resolveFunctionStep(UUID appUserId, FlowStep step) {
        try {
            FunctionVersionSourceDetailResponse function =
                    loadFunctionSource(appUserId, step.getComponentId(), step.getComponentVersionId());
            return new FlowStepSourceResponse(
                    step.getStepKey(), step.getComponentType().name(), step.getPosition(),
                    step.getComponentId(), step.getComponentVersionId(), function, null, null);
        } catch (ResourceNotFoundException exception) {
            return new FlowStepSourceResponse(
                    step.getStepKey(), step.getComponentType().name(), step.getPosition(),
                    step.getComponentId(), step.getComponentVersionId(), null, null, exception.getMessage());
        }
    }

    private FunctionVersionSourceDetailResponse loadFunctionSource(UUID appUserId, UUID functionId, UUID functionVersionId) {
        Function function = functionService.getFunctionById(appUserId, functionId);
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, functionVersionId);
        SourceBundle bundle = functionVersionSourceService.findSource(functionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No source submitted for function version: " + functionVersionId));

        List<FunctionVersionSourceFileResponse> files = bundle.files().stream()
                .map(file -> new FunctionVersionSourceFileResponse(file.relativePath(), file.content()))
                .toList();
        return new FunctionVersionSourceDetailResponse(
                function.getId(), function.getFunctionKey(), functionVersion.getId(), functionVersion.getVersion(),
                functionVersion.getStatus().name(), functionVersion.getRuntime(),
                bundle.entrypoint(), bundle.handler(), files);
    }

    private FlowStepSourceResponse resolveSubFlowStep(UUID appUserId, FlowStep step, Set<UUID> visitedFlowVersionIds) {
        UUID subFlowId = step.getComponentId();
        UUID subFlowVersionId = step.getComponentVersionId();

        if (visitedFlowVersionIds.contains(subFlowVersionId)) {
            return new FlowStepSourceResponse(
                    step.getStepKey(), step.getComponentType().name(), step.getPosition(),
                    subFlowId, subFlowVersionId, null, null,
                    "Cycle detected: FlowVersion " + subFlowVersionId
                            + " is already an ancestor of this step - not expanding further");
        }

        Set<UUID> nextVisited = new LinkedHashSet<>(visitedFlowVersionIds);
        nextVisited.add(subFlowVersionId);
        try {
            FlowFullSourceResponse subFlow = resolveFlow(appUserId, subFlowId, subFlowVersionId, nextVisited);
            return new FlowStepSourceResponse(
                    step.getStepKey(), step.getComponentType().name(), step.getPosition(),
                    subFlowId, subFlowVersionId, null, subFlow, null);
        } catch (ResourceNotFoundException exception) {
            return new FlowStepSourceResponse(
                    step.getStepKey(), step.getComponentType().name(), step.getPosition(),
                    subFlowId, subFlowVersionId, null, null, exception.getMessage());
        }
    }
}
