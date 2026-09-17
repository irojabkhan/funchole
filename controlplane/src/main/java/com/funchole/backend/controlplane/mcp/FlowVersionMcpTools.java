package com.funchole.backend.controlplane.mcp;

import com.funchole.backend.controlplane.constant.FlowStepComponentType;
import com.funchole.backend.controlplane.dto.FlowStepCreateRequest;
import com.funchole.backend.controlplane.dto.FlowStepResponse;
import com.funchole.backend.controlplane.dto.FlowStepUpdateRequest;
import com.funchole.backend.controlplane.dto.FlowVersionCreateRequest;
import com.funchole.backend.controlplane.dto.FlowVersionResponse;
import com.funchole.backend.controlplane.entity.FlowStep;
import com.funchole.backend.controlplane.entity.FlowVersion;
import com.funchole.backend.controlplane.mapper.FlowStepMapper;
import com.funchole.backend.controlplane.mapper.FlowVersionMapper;
import com.funchole.backend.controlplane.service.FlowStepService;
import com.funchole.backend.controlplane.service.FlowVersionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * MCP tool surface for the FlowVersion lifecycle (draft -> steps -> adopt ->
 * archive) and its FlowSteps - mirrors {@code FlowVersionController} and
 * {@code FlowStepController} exactly.
 */
@Service
public class FlowVersionMcpTools {

    private final FlowVersionService flowVersionService;
    private final FlowStepService flowStepService;
    private final FlowVersionMapper flowVersionMapper;
    private final FlowStepMapper flowStepMapper;

    public FlowVersionMcpTools(
            FlowVersionService flowVersionService,
            FlowStepService flowStepService,
            FlowVersionMapper flowVersionMapper,
            FlowStepMapper flowStepMapper
    ) {
        this.flowVersionService = flowVersionService;
        this.flowStepService = flowStepService;
        this.flowVersionMapper = flowVersionMapper;
        this.flowStepMapper = flowStepMapper;
    }

    @McpTool(name = "list_flow_versions", description = "List a Flow's versions, most recent first.")
    public List<FlowVersionResponse> listFlowVersions(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "1-based page number, defaults to 1", required = false) Integer page,
            @McpToolParam(description = "Page size, defaults to 20", required = false) Integer size
    ) {
        Page<FlowVersionResponse> result = flowVersionService
                .listVersions(CurrentMcpUser.id(), UUID.fromString(flowId), page != null ? page : 1, size != null ? size : 20)
                .map(flowVersionMapper::toResponse);
        return result.getContent();
    }

    @McpTool(name = "get_flow_version", description = "Get one FlowVersion by id, including its ordered list of steps.")
    public FlowVersionResponse getFlowVersion(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId
    ) {
        UUID flowUuid = UUID.fromString(flowId);
        FlowVersion flowVersion = flowVersionService.getVersionById(CurrentMcpUser.id(), flowUuid, UUID.fromString(versionId));
        return toResponseWithSteps(flowUuid, flowVersion);
    }

    @McpTool(
            name = "create_flow_version",
            description = "Create a new DRAFT FlowVersion under a Flow. Add steps to it next with "
                    + "create_flow_step, then adopt_flow_version to make it the live route."
    )
    public FlowVersionResponse createFlowVersion(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "Runtime, defaults to NODE", required = false) String runtime,
            @McpToolParam(description = "Free-form metadata string - optional", required = false) String metadata
    ) {
        FlowVersion version = flowVersionService.createDraftVersion(
                CurrentMcpUser.id(), UUID.fromString(flowId), new FlowVersionCreateRequest(runtime, metadata));
        return flowVersionMapper.toResponse(version);
    }

    @McpTool(name = "adopt_flow_version", description = "Adopt a FlowVersion, making it the live version its Flow's route serves.")
    public FlowVersionResponse adoptFlowVersion(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId
    ) {
        FlowVersion version = flowVersionService.adoptVersion(CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId));
        return flowVersionMapper.toResponse(version);
    }

    @McpTool(name = "archive_flow_version", description = "Archive a FlowVersion so it no longer serves traffic.")
    public FlowVersionResponse archiveFlowVersion(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId
    ) {
        FlowVersion version = flowVersionService.archiveVersion(CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId));
        return flowVersionMapper.toResponse(version);
    }

    @McpTool(name = "delete_flow_version", description = "Delete a DRAFT FlowVersion.")
    public Map<String, String> deleteFlowVersion(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId
    ) {
        flowVersionService.deleteDraftVersion(CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId));
        return Map.of("message", "Flow version deleted successfully");
    }

    @McpTool(name = "list_flow_steps", description = "List a FlowVersion's steps in order.")
    public List<FlowStepResponse> listFlowSteps(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId
    ) {
        return flowStepService.listSteps(CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId))
                .stream().map(flowStepMapper::toResponse).toList();
    }

    @McpTool(
            name = "create_flow_step",
            description = "Add a step to a DRAFT FlowVersion. componentType is one of FUNCTION, RESPONSE, "
                    + "MIDDLEWARE, SUB_FLOW; componentId/componentVersionId identify the Function/FlowVersion the "
                    + "step runs (both are that resource's own id for a FUNCTION step's componentId+its "
                    + "FunctionVersion id, or the sub-flow's Flow id + its FlowVersion id for SUB_FLOW)."
    )
    public FlowStepResponse createFlowStep(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId,
            @McpToolParam(description = "Unique step key within this version, e.g. 'list-orders'") String stepKey,
            @McpToolParam(description = "One of FUNCTION, RESPONSE, MIDDLEWARE, SUB_FLOW") String componentType,
            @McpToolParam(description = "1-based order in the step sequence") Integer position,
            @McpToolParam(description = "The referenced Function/Flow id (UUID)") String componentId,
            @McpToolParam(description = "The referenced FunctionVersion/FlowVersion id (UUID)") String componentVersionId,
            @McpToolParam(description = "Raw JSON text stored as-is alongside the step - optional", required = false) String metadata
    ) {
        FlowStep step = flowStepService.createStep(
                CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId),
                new FlowStepCreateRequest(
                        stepKey,
                        FlowStepComponentType.valueOf(componentType),
                        position,
                        UUID.fromString(componentId),
                        UUID.fromString(componentVersionId),
                        metadata
                ));
        return flowStepMapper.toResponse(step);
    }

    @McpTool(name = "update_flow_step", description = "Update a step on a DRAFT FlowVersion.")
    public FlowStepResponse updateFlowStep(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId,
            @McpToolParam(description = "FlowStep id (UUID)") String stepId,
            @McpToolParam(description = "Unique step key within this version") String stepKey,
            @McpToolParam(description = "One of FUNCTION, RESPONSE, MIDDLEWARE, SUB_FLOW") String componentType,
            @McpToolParam(description = "1-based order in the step sequence") Integer position,
            @McpToolParam(description = "The referenced Function/Flow id (UUID)") String componentId,
            @McpToolParam(description = "The referenced FunctionVersion/FlowVersion id (UUID)") String componentVersionId,
            @McpToolParam(description = "Raw JSON text stored as-is alongside the step - optional", required = false) String metadata
    ) {
        FlowStep step = flowStepService.updateStep(
                CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId), UUID.fromString(stepId),
                new FlowStepUpdateRequest(
                        stepKey,
                        FlowStepComponentType.valueOf(componentType),
                        position,
                        UUID.fromString(componentId),
                        UUID.fromString(componentVersionId),
                        metadata
                ));
        return flowStepMapper.toResponse(step);
    }

    @McpTool(name = "delete_flow_step", description = "Remove a step from a DRAFT FlowVersion.")
    public Map<String, String> deleteFlowStep(
            @McpToolParam(description = "Flow id (UUID)") String flowId,
            @McpToolParam(description = "FlowVersion id (UUID)") String versionId,
            @McpToolParam(description = "FlowStep id (UUID)") String stepId
    ) {
        flowStepService.deleteStep(CurrentMcpUser.id(), UUID.fromString(flowId), UUID.fromString(versionId), UUID.fromString(stepId));
        return Map.of("message", "Flow step deleted successfully");
    }

    private FlowVersionResponse toResponseWithSteps(UUID flowId, FlowVersion flowVersion) {
        FlowVersionResponse base = flowVersionMapper.toResponse(flowVersion);
        List<FlowStepResponse> steps = flowStepService.listSteps(CurrentMcpUser.id(), flowId, flowVersion.getId())
                .stream().map(flowStepMapper::toResponse).toList();
        return new FlowVersionResponse(
                base.id(), base.flowId(), base.version(), base.status(), base.runtime(), base.metadata(),
                steps, base.createdAt(), base.updatedAt(), base.adoptedAt(), base.archivedAt());
    }
}
