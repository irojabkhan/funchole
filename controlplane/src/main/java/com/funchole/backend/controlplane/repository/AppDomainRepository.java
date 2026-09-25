package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.constant.DomainStatus;
import com.funchole.backend.controlplane.entity.AppDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppDomainRepository extends JpaRepository<AppDomain, UUID> {

    Page<AppDomain> findAllByAppUserId(UUID appUserId, Pageable pageable);

    Optional<AppDomain> findByIdAndAppUser_Id(UUID id, UUID appUserId);

    long countByAppUser_Id(UUID appUserId);

    List<AppDomain> findAllByStatus(DomainStatus status);
}
