package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A per-user exception on top of their assigned {@link Package} - e.g.
 * "give this one user +1 gateway" without creating a whole new package
 * tier. Read-only from application code: an operator inserts/updates a row
 * directly in the database (see V26); {@code PackageLimitService} checks
 * this table before falling back to the user's package limit. {@code
 * limitValue} of {@code null} means unlimited for that key, same as
 * {@link PackageLimit}.
 */
@Entity
@Table(name = "user_package_overrides")
public class UserPackageOverride {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "limit_key", nullable = false, length = 100)
    private String limitKey;

    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public String getLimitKey() {
        return limitKey;
    }

    public Integer getLimitValue() {
        return limitValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
