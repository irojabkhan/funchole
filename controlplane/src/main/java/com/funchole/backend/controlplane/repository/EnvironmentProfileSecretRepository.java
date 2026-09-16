package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.EnvironmentProfileSecret;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentProfileSecretRepository extends JpaRepository<EnvironmentProfileSecret, UUID> {

    List<EnvironmentProfileSecret> findAllByEnvironmentProfile_IdOrderByKeyAsc(UUID environmentProfileId);

    Optional<EnvironmentProfileSecret> findByEnvironmentProfile_IdAndKey(UUID environmentProfileId, String key);
}
