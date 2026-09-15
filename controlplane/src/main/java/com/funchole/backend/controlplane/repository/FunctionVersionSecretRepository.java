package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.FunctionVersionSecret;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionVersionSecretRepository extends JpaRepository<FunctionVersionSecret, UUID> {

    List<FunctionVersionSecret> findAllByFunctionVersion_IdOrderByKeyAsc(UUID functionVersionId);

    Optional<FunctionVersionSecret> findByFunctionVersion_IdAndKey(UUID functionVersionId, String key);
}
