package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.EnvironmentProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentProfileRepository extends JpaRepository<EnvironmentProfile, UUID> {

    Page<EnvironmentProfile> findAllByAppUser_IdAndDeletedAtIsNull(UUID appUserId, Pageable pageable);

    Optional<EnvironmentProfile> findByIdAndAppUser_IdAndDeletedAtIsNull(UUID id, UUID appUserId);

    boolean existsByAppUser_IdAndEnvironmentKeyAndDeletedAtIsNull(UUID appUserId, String environmentKey);
}
