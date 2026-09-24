package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.PackageLimit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageLimitRepository extends JpaRepository<PackageLimit, UUID> {

    Optional<PackageLimit> findByPackageIdAndLimitKey(UUID packageId, String limitKey);
}
