package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.Package;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageRepository extends JpaRepository<Package, UUID> {

    Optional<Package> findByKey(String key);
}
