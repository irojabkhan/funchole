package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FunctionVersionDatabaseAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionVersionDatabaseAttachmentRepository extends JpaRepository<FunctionVersionDatabaseAttachment, UUID> {

    List<FunctionVersionDatabaseAttachment> findAllByFunctionVersion_IdOrderByCreatedAtAsc(UUID functionVersionId);

    Optional<FunctionVersionDatabaseAttachment> findByFunctionVersion_IdAndDatabase_Id(UUID functionVersionId, UUID databaseId);
}
