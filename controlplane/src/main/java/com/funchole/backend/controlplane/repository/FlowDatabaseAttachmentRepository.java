package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FlowDatabaseAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowDatabaseAttachmentRepository extends JpaRepository<FlowDatabaseAttachment, UUID> {

    @EntityGraph(attributePaths = { "database" })
    List<FlowDatabaseAttachment> findAllByFlow_IdOrderByCreatedAtAsc(UUID flowId);

    Optional<FlowDatabaseAttachment> findByFlow_IdAndDatabase_Id(UUID flowId, UUID databaseId);
}
