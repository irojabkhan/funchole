package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FunctionVersionEnvVar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionVersionEnvVarRepository extends JpaRepository<FunctionVersionEnvVar, UUID> {

    List<FunctionVersionEnvVar> findAllByFunctionVersion_IdOrderByKeyAsc(UUID functionVersionId);

    Optional<FunctionVersionEnvVar> findByFunctionVersion_IdAndKey(UUID functionVersionId, String key);
}
