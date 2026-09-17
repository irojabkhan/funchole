package com.funchole.backend.controlplane.repository;

import com.funchole.backend.controlplane.entity.AppUserApiKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserApiKeyRepository extends JpaRepository<AppUserApiKey, UUID> {

    // JOIN FETCH so the resolved AppUser is fully initialized before this
    // method's transaction closes - callers (ApiKeyAuthenticationFilter)
    // touch it later, outside any transaction of their own.
    @Query("SELECT k FROM AppUserApiKey k JOIN FETCH k.appUser WHERE k.hashedKey = :hashedKey AND k.revokedAt IS NULL")
    Optional<AppUserApiKey> findByHashedKeyAndRevokedAtIsNull(String hashedKey);

    List<AppUserApiKey> findAllByAppUser_IdOrderByCreatedAtDesc(UUID appUserId);

    Optional<AppUserApiKey> findByIdAndAppUser_Id(UUID id, UUID appUserId);
}
