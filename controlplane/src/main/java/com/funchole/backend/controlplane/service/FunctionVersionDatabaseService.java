package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.FunctionVersionDatabaseAttachmentResponse;
import com.funchole.backend.controlplane.entity.Database;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import com.funchole.backend.controlplane.entity.FunctionVersionDatabaseAttachment;
import com.funchole.backend.controlplane.repository.FunctionVersionDatabaseAttachmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FunctionVersionDatabaseService {

    private final FunctionVersionService functionVersionService;
    private final DatabaseService databaseService;
    private final FunctionVersionDatabaseAttachmentRepository attachmentRepository;

    public FunctionVersionDatabaseService(
            FunctionVersionService functionVersionService,
            DatabaseService databaseService,
            FunctionVersionDatabaseAttachmentRepository attachmentRepository
    ) {
        this.functionVersionService = functionVersionService;
        this.databaseService = databaseService;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional(readOnly = true)
    public List<FunctionVersionDatabaseAttachmentResponse> listAttachments(UUID appUserId, UUID functionId, UUID versionId) {
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        return attachmentRepository.findAllByFunctionVersion_IdOrderByCreatedAtAsc(functionVersion.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Copies every Database attachment from {@code source} onto {@code target}
     * - used by {@code FunctionVersionCloneService} when a new version is
     * auto-seeded from the Function's most recent version. Each attachment
     * row just links to the same {@link Database}; nothing about the
     * Database itself is copied or re-provisioned.
     */
    @Transactional
    public void cloneAttachments(FunctionVersion source, FunctionVersion target) {
        for (FunctionVersionDatabaseAttachment attachment :
                attachmentRepository.findAllByFunctionVersion_IdOrderByCreatedAtAsc(source.getId())) {
            attachmentRepository.save(FunctionVersionDatabaseAttachment.create(target, attachment.getDatabase()));
        }
    }

    @Transactional
    public List<FunctionVersionDatabaseAttachmentResponse> attachDatabase(
            UUID appUserId,
            UUID functionId,
            UUID versionId,
            UUID databaseId
    ) {
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        Database database = databaseService.getDatabaseById(appUserId, databaseId);

        attachmentRepository.findByFunctionVersion_IdAndDatabase_Id(functionVersion.getId(), database.getId())
                .orElseGet(() -> attachmentRepository.save(FunctionVersionDatabaseAttachment.create(functionVersion, database)));

        return listAttachments(appUserId, functionId, versionId);
    }

    @Transactional
    public List<FunctionVersionDatabaseAttachmentResponse> detachDatabase(
            UUID appUserId,
            UUID functionId,
            UUID versionId,
            UUID databaseId
    ) {
        FunctionVersion functionVersion = functionVersionService.getVersionById(appUserId, functionId, versionId);
        Database database = databaseService.getDatabaseById(appUserId, databaseId);

        attachmentRepository.findByFunctionVersion_IdAndDatabase_Id(functionVersion.getId(), database.getId())
                .ifPresent(attachmentRepository::delete);

        return listAttachments(appUserId, functionId, versionId);
    }

    private FunctionVersionDatabaseAttachmentResponse toResponse(FunctionVersionDatabaseAttachment attachment) {
        Database database = attachment.getDatabase();
        return new FunctionVersionDatabaseAttachmentResponse(
                attachment.getId(),
                database.getId(),
                database.getName(),
                database.getType(),
                attachment.getCreatedAt()
        );
    }
}
