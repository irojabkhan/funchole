package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.Database;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseRepository extends JpaRepository<Database, UUID> {

    Page<Database> findAllByAppUser_IdAndDeletedAtIsNull(UUID appUserId, Pageable pageable);

    Optional<Database> findByIdAndAppUser_IdAndDeletedAtIsNull(UUID id, UUID appUserId);

    boolean existsByAppUser_IdAndNameAndDeletedAtIsNull(UUID appUserId, String name);

    long countByAppUser_IdAndDeletedAtIsNull(UUID appUserId);

    // Global (not per-user) - the generated Postgres identifier itself must
    // be unique across every tenant on the shared tenant-db server, not just
    // unique within one user's own rows.
    boolean existsByDatabaseName(String databaseName);
}
