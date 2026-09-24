package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.UserPackage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPackageRepository extends JpaRepository<UserPackage, UUID> {

    Optional<UserPackage> findByAppUser_Id(UUID appUserId);
}
