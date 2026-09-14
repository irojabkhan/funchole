package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.constant.FlowVersionStatus;
import com.funchole.backend.controlplane.dto.FlowStepCreateRequest;
import com.funchole.backend.controlplane.dto.FlowStepUpdateRequest;
import com.funchole.backend.controlplane.entity.FlowStep;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.repository.FlowStepRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowStepService {

    private final FlowStepRepository flowStepRepository;
    private final FlowVersionService flowVersionService;
    private final FlowStepReferenceValidator flowStepReferenceValidator;

    public FlowStepService(
            FlowStepRepository flowStepRepository,
            FlowVersionService flowVersionService,
            FlowStepReferenceValidator flowStepReferenceValidator
    ) {
        this.flowStepRepository = flowStepRepository;
        this.flowVersionService = flowVersionService;
        this.flowStepReferenceValidator = flowStepReferenceValidator;
    }

    public List<FlowStep> listSteps(UUID appUserId, UUID flowId, UUID versionId) {
        flowVersionService.getVersionById(appUserId, flowId, versionId);
        return flowStepRepository.findAllByFlowVersion_IdOrderByPosition(versionId);
    }

    @Transactional
    public FlowStep createStep(UUID appUserId, UUID flowId, UUID versionId, FlowStepCreateRequest request) {
        FlowVersion flowVersion = getDraftVersion(appUserId, flowId, versionId);
        flowStepReferenceValidator.validateComponentReference(
                appUserId, request.componentType(), request.componentId(), request.componentVersionId());
        requirePositionAvailable(versionId, request.position(), null);

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
        flowStepReferenceValidator.validateComponentReference(
                appUserId, request.componentType(), request.componentId(), request.componentVersionId());
        requirePositionAvailable(versionId, request.position(), stepId);

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
     * The DB has a unique (flow_version_id, position) constraint, but relying
     * on it alone surfaces a duplicate as an opaque 500 whenever Hibernate
     * happens to flush (which may not be immediately on save) rather than a
     * clean 4xx at the point of the actual mistake. {@code excludeStepId}
     * lets an update leave a step at its own current position unchanged.
     */
    private void requirePositionAvailable(UUID versionId, int position, UUID excludeStepId) {
        flowStepRepository.findByFlowVersion_IdAndPosition(versionId, position)
                .filter(existing -> !existing.getId().equals(excludeStepId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Position " + position + " is already used by step '" + existing.getStepKey() + "' in this flow version");
                });
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
