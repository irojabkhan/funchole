package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.UserPackageOverride;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPackageOverrideRepository extends JpaRepository<UserPackageOverride, UUID> {

    Optional<UserPackageOverride> findByAppUser_IdAndLimitKey(UUID appUserId, String limitKey);
}
