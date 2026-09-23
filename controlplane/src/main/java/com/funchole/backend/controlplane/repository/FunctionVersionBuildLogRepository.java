package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FunctionVersionBuildLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionVersionBuildLogRepository extends JpaRepository<FunctionVersionBuildLog, UUID> {

    List<FunctionVersionBuildLog> findAllByFunctionVersionIdOrderByCreatedAtAsc(UUID functionVersionId);
}
