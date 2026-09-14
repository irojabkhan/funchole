package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.constant.FlowStepComponentType;
import com.funchole.backend.controlplane.constant.FlowVersionStatus;
import com.funchole.backend.controlplane.constant.FunctionVersionStatus;
import com.funchole.backend.controlplane.dto.FlowStepCreateRequest;
import com.funchole.backend.controlplane.dto.FlowStepUpdateRequest;
import com.funchole.backend.controlplane.entity.Flow;
import com.funchole.backend.controlplane.entity.FlowStep;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.repository.FlowRepository;
import com.funchole.backend.controlplane.repository.FlowStepRepository;
import com.funchole.backend.controlplane.repository.FlowVersionRepository;
import com.funchole.backend.controlplane.repository.FunctionRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowStepService {

    private static final Set<FlowStepComponentType> FUNCTION_BACKED_TYPES =
            Set.of(FlowStepComponentType.FUNCTION, FlowStepComponentType.RESPONSE, FlowStepComponentType.MIDDLEWARE);

    private final FlowStepRepository flowStepRepository;
    private final FlowVersionService flowVersionService;
    private final FunctionRepository functionRepository;
    private final FunctionVersionRepository functionVersionRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;

    public FlowStepService(
            FlowStepRepository flowStepRepository,
            FlowVersionService flowVersionService,
            FunctionRepository functionRepository,
            FunctionVersionRepository functionVersionRepository,
            FlowRepository flowRepository,
            FlowVersionRepository flowVersionRepository
    ) {
        this.flowStepRepository = flowStepRepository;
        this.flowVersionService = flowVersionService;
        this.functionRepository = functionRepository;
        this.functionVersionRepository = functionVersionRepository;
        this.flowRepository = flowRepository;
        this.flowVersionRepository = flowVersionRepository;
    }

    public List<FlowStep> listSteps(UUID appUserId, UUID flowId, UUID versionId) {
        flowVersionService.getVersionById(appUserId, flowId, versionId);
        return flowStepRepository.findAllByFlowVersion_IdOrderByPosition(versionId);
    }

    @Transactional
    public FlowStep createStep(UUID appUserId, UUID flowId, UUID versionId, FlowStepCreateRequest request) {
        FlowVersion flowVersion = getDraftVersion(appUserId, flowId, versionId);
        validateComponentReference(appUserId, request.componentType(), request.componentId(), request.componentVersionId());

        FlowStep flowStep = FlowStep.create(
                flowVersion,
                request.stepKey(),
                request.componentType(),
                request.position(),
                request.componentId(),
                request.componentVersionId(),
                request.metadata()
        );

        return flowStepRepository.save(flowStep);
    }

    @Transactional
    public FlowStep updateStep(UUID appUserId, UUID flowId, UUID versionId, UUID stepId, FlowStepUpdateRequest request) {
        getDraftVersion(appUserId, flowId, versionId);
        FlowStep flowStep = getStep(versionId, stepId);
        validateComponentReference(appUserId, request.componentType(), request.componentId(), request.componentVersionId());

        flowStep.update(
                request.stepKey(),
                request.componentType(),
                request.position(),
                request.componentId(),
                request.componentVersionId(),
                request.metadata()
        );

        return flowStepRepository.save(flowStep);
    }

    @Transactional
    public void deleteStep(UUID appUserId, UUID flowId, UUID versionId, UUID stepId) {
        getDraftVersion(appUserId, flowId, versionId);
        FlowStep flowStep = getStep(versionId, stepId);
        flowStepRepository.delete(flowStep);
    }

    /**
     * FUNCTION/RESPONSE/MIDDLEWARE steps must reference a real, owned, READY
     * FunctionVersion; SUB_FLOW steps must reference a real, owned, ADOPTED
     * FlowVersion. No authoring-time cycle check is done here for SUB_FLOW -
     * the invocation module's snapshot resolution already rejects a cyclic
     * graph at invocation-creation time, which is sufficient.
     */
    private void validateComponentReference(
            UUID appUserId, FlowStepComponentType componentType, UUID componentId, UUID componentVersionId
    ) {
        if (FUNCTION_BACKED_TYPES.contains(componentType)) {
            Function function = functionRepository.findByIdAndAppUser_IdAndDeletedAtIsNull(componentId, appUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Function not found: " + componentId));
            FunctionVersion functionVersion = functionVersionRepository.findByIdAndFunction_Id(componentVersionId, componentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Function version " + componentVersionId + " does not belong to function " + function.getId()));
            if (functionVersion.getStatus() != FunctionVersionStatus.READY) {
                throw new IllegalArgumentException(
                        "Function version must be READY to be referenced by a Flow step, current status is "
                                + functionVersion.getStatus() + ": " + componentVersionId);
            }
            return;
        }

        // SUB_FLOW
        Flow flow = flowRepository.findByIdAndAppUser_IdAndDeletedAtIsNull(componentId, appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + componentId));
        FlowVersion referencedVersion = flowVersionRepository.findByIdAndFlow_Id(componentVersionId, componentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Flow version " + componentVersionId + " does not belong to flow " + flow.getId()));
        if (referencedVersion.getStatus() != FlowVersionStatus.ADOPTED) {
            throw new IllegalArgumentException(
                    "Flow version must be ADOPTED to be referenced by a SUB_FLOW step, current status is "
                            + referencedVersion.getStatus() + ": " + componentVersionId);
        }
    }

    private FlowVersion getDraftVersion(UUID appUserId, UUID flowId, UUID versionId) {
        FlowVersion flowVersion = flowVersionService.getVersionById(appUserId, flowId, versionId);
        if (flowVersion.getStatus() != FlowVersionStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Steps can only be changed while the flow version is DRAFT, current status: " + flowVersion.getStatus());
        }
        return flowVersion;
    }

    private FlowStep getStep(UUID versionId, UUID stepId) {
        return flowStepRepository.findByIdAndFlowVersion_Id(stepId, versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow step not found: " + stepId));
    }
}
