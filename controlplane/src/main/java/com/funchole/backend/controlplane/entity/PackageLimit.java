package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One numeric limit for one {@link Package} - e.g. {@code MAX_GATEWAYS=1}
 * for the free package. Plain {@code packageId} column rather than a
 * {@code @ManyToOne} relationship - every lookup here is by id, nothing
 * ever needs to navigate to the full {@link Package} entity from this
 * side, and "package" being a Java keyword makes a relationship field's
 * naming needlessly awkward. {@code limitKey} is a plain string (not the
 * {@code PackageLimitKey} enum) so a row can exist for a limit type this
 * codebase doesn't enforce yet. {@code limitValue} of {@code null} means
 * unlimited. Read-only from application code - rows are seeded by
 * migration and edited directly in the database (see V26).
 */
@Entity
@Table(name = "package_limits")
public class PackageLimit {

    @Id
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

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

    public UUID getPackageId() {
        return packageId;
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
