package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FlowEnvironmentAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowEnvironmentAttachmentRepository extends JpaRepository<FlowEnvironmentAttachment, UUID> {

    @EntityGraph(attributePaths = { "environmentProfile" })
    List<FlowEnvironmentAttachment> findAllByFlow_IdOrderByPriorityAscCreatedAtAsc(UUID flowId);

    Optional<FlowEnvironmentAttachment> findByFlow_IdAndEnvironmentProfile_Id(UUID flowId, UUID environmentProfileId);
}
