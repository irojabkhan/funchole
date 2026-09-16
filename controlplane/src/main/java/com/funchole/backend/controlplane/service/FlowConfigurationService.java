package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FlowDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.dto.FlowEnvironmentAttachmentResponse;
import com.funchole.backend.controlplane.entity.Database;
import com.funchole.backend.controlplane.entity.EnvironmentProfile;
import com.funchole.backend.controlplane.entity.Flow;
import com.funchole.backend.controlplane.entity.FlowDatabaseAttachment;
import com.funchole.backend.controlplane.entity.FlowEnvironmentAttachment;
import com.funchole.backend.controlplane.repository.FlowDatabaseAttachmentRepository;
import com.funchole.backend.controlplane.repository.FlowEnvironmentAttachmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowConfigurationService {

    private static final int DEFAULT_PRIORITY = 100;

    private final FlowService flowService;
    private final EnvironmentProfileService environmentProfileService;
    private final DatabaseService databaseService;
    private final FlowEnvironmentAttachmentRepository environmentAttachmentRepository;
    private final FlowDatabaseAttachmentRepository databaseAttachmentRepository;

    public FlowConfigurationService(
            FlowService flowService,
            EnvironmentProfileService environmentProfileService,
            DatabaseService databaseService,
            FlowEnvironmentAttachmentRepository environmentAttachmentRepository,
            FlowDatabaseAttachmentRepository databaseAttachmentRepository
    ) {
        this.flowService = flowService;
        this.environmentProfileService = environmentProfileService;
        this.databaseService = databaseService;
        this.environmentAttachmentRepository = environmentAttachmentRepository;
        this.databaseAttachmentRepository = databaseAttachmentRepository;
    }

    @Transactional(readOnly = true)
    public List<FlowEnvironmentAttachmentResponse> listEnvironmentAttachments(UUID appUserId, UUID flowId) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        return environmentAttachmentRepository.findAllByFlow_IdOrderByPriorityAscCreatedAtAsc(flow.getId())
                .stream()
                .map(this::toEnvironmentAttachmentResponse)
                .toList();
    }

    @Transactional
    public List<FlowEnvironmentAttachmentResponse> attachEnvironment(
            UUID appUserId,
            UUID flowId,
            UUID environmentProfileId,
            Integer priority
    ) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        EnvironmentProfile profile = environmentProfileService.getProfileById(appUserId, environmentProfileId);
        FlowEnvironmentAttachment attachment = environmentAttachmentRepository
                .findByFlow_IdAndEnvironmentProfile_Id(flow.getId(), profile.getId())
                .map(existing -> {
                    existing.updatePriority(resolvePriority(priority));
                    return existing;
                })
                .orElseGet(() -> FlowEnvironmentAttachment.create(flow, profile, resolvePriority(priority)));
        environmentAttachmentRepository.save(attachment);
        return listEnvironmentAttachments(appUserId, flowId);
    }

    @Transactional
    public List<FlowEnvironmentAttachmentResponse> detachEnvironment(UUID appUserId, UUID flowId, UUID environmentProfileId) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        environmentProfileService.getProfileById(appUserId, environmentProfileId);
        environmentAttachmentRepository.findByFlow_IdAndEnvironmentProfile_Id(flow.getId(), environmentProfileId)
                .ifPresent(environmentAttachmentRepository::delete);
        return listEnvironmentAttachments(appUserId, flowId);
    }

    @Transactional(readOnly = true)
    public List<FlowDatabaseAttachmentResponse> listDatabaseAttachments(UUID appUserId, UUID flowId) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        return databaseAttachmentRepository.findAllByFlow_IdOrderByCreatedAtAsc(flow.getId())
                .stream()
                .map(this::toDatabaseAttachmentResponse)
                .toList();
    }

    @Transactional
    public List<FlowDatabaseAttachmentResponse> attachDatabase(UUID appUserId, UUID flowId, UUID databaseId) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        Database database = databaseService.getDatabaseById(appUserId, databaseId);
        databaseAttachmentRepository.findByFlow_IdAndDatabase_Id(flow.getId(), database.getId())
                .orElseGet(() -> databaseAttachmentRepository.save(FlowDatabaseAttachment.create(flow, database)));
        return listDatabaseAttachments(appUserId, flowId);
    }

    @Transactional
    public List<FlowDatabaseAttachmentResponse> detachDatabase(UUID appUserId, UUID flowId, UUID databaseId) {
        Flow flow = flowService.getFlowById(appUserId, flowId);
        databaseService.getDatabaseById(appUserId, databaseId);
        databaseAttachmentRepository.findByFlow_IdAndDatabase_Id(flow.getId(), databaseId)
                .ifPresent(databaseAttachmentRepository::delete);
        return listDatabaseAttachments(appUserId, flowId);
    }

    private int resolvePriority(Integer priority) {
        return priority == null ? DEFAULT_PRIORITY : priority;
    }

    private FlowEnvironmentAttachmentResponse toEnvironmentAttachmentResponse(FlowEnvironmentAttachment attachment) {
        EnvironmentProfile profile = attachment.getEnvironmentProfile();
        return new FlowEnvironmentAttachmentResponse(
                attachment.getId(),
                profile.getId(),
                profile.getEnvironmentKey(),
                profile.getName(),
                attachment.getPriority(),
                attachment.getCreatedAt()
        );
    }

    private FlowDatabaseAttachmentResponse toDatabaseAttachmentResponse(FlowDatabaseAttachment attachment) {
        Database database = attachment.getDatabase();
        return new FlowDatabaseAttachmentResponse(
                attachment.getId(),
                database.getId(),
                database.getName(),
                database.getType(),
                attachment.getCreatedAt()
        );
    }
}
