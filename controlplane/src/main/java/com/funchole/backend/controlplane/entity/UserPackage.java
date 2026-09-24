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
 * The one active {@link Package} assigned to an {@link AppUser} - cloud
 * mode only. Created once at self-registration time (see
 * {@code CloudSignupService}); there is no upgrade/downgrade flow yet, so
 * changing a user's package today means updating this row's
 * {@code package_id} directly in the database. {@code packageId} is a
 * plain column, not a relationship - see {@link PackageLimit}'s javadoc
 * for why.
 */
@Entity
@Table(name = "user_packages")
public class UserPackage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public UUID getPackageId() {
        return packageId;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public static UserPackage assign(AppUser appUser, UUID packageId) {
        UserPackage userPackage = new UserPackage();
        OffsetDateTime now = OffsetDateTime.now();
        userPackage.id = UUID.randomUUID();
        userPackage.appUser = appUser;
        userPackage.packageId = packageId;
        userPackage.assignedAt = now;
        userPackage.updatedAt = now;
        return userPackage;
    }
}
