package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.EnvironmentProfileEnvVar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentProfileEnvVarRepository extends JpaRepository<EnvironmentProfileEnvVar, UUID> {

    List<EnvironmentProfileEnvVar> findAllByEnvironmentProfile_IdOrderByKeyAsc(UUID environmentProfileId);

    Optional<EnvironmentProfileEnvVar> findByEnvironmentProfile_IdAndKey(UUID environmentProfileId, String key);
}
