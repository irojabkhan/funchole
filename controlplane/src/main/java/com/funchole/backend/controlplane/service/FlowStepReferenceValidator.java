package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.constant.FlowStepComponentType;
import com.funchole.backend.controlplane.constant.FlowVersionStatus;
import com.funchole.backend.controlplane.constant.FunctionVersionStatus;
import com.funchole.backend.controlplane.entity.Flow;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.entity.Function;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.repository.FlowRepository;
import com.funchole.backend.controlplane.repository.FlowVersionRepository;
import com.funchole.backend.controlplane.repository.FunctionRepository;
import com.funchole.backend.controlplane.repository.FunctionVersionRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared by {@link FlowStepService} (at step create/update time) and
 * {@link FlowVersionService} (re-checked at adopt time, since a step's
 * Function/FunctionVersion or referenced Flow/FlowVersion can drift - e.g.
 * soft-deleted - between when the step was authored and when the version is
 * adopted).
 */
@Component
class FlowStepReferenceValidator {

    private static final Set<FlowStepComponentType> FUNCTION_BACKED_TYPES =
            Set.of(FlowStepComponentType.FUNCTION, FlowStepComponentType.RESPONSE, FlowStepComponentType.MIDDLEWARE);

    private final FunctionRepository functionRepository;
    private final FunctionVersionRepository functionVersionRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;

    FlowStepReferenceValidator(
            FunctionRepository functionRepository,
            FunctionVersionRepository functionVersionRepository,
            FlowRepository flowRepository,
            FlowVersionRepository flowVersionRepository
    ) {
        this.functionRepository = functionRepository;
        this.functionVersionRepository = functionVersionRepository;
        this.flowRepository = flowRepository;
        this.flowVersionRepository = flowVersionRepository;
    }

    /**
     * FUNCTION/RESPONSE/MIDDLEWARE steps must reference a real, owned, READY
     * FunctionVersion; SUB_FLOW steps must reference a real, owned, ADOPTED
     * FlowVersion. No cycle check is done here for SUB_FLOW - the invocation
     * module's snapshot resolution already rejects a cyclic graph at
     * invocation-creation time, which is sufficient.
     */
    void validateComponentReference(
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
}
