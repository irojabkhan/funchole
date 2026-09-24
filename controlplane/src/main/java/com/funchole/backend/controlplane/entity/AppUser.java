package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Self-registration via Google sign-in (cloud mode only - see
     * {@code CloudSignupService}). {@code passwordHash} is a random,
     * unusable placeholder the caller generates (this account only ever
     * authenticates via a verified Google ID token, never a password) -
     * kept as a real parameter rather than generated here so this entity
     * stays framework-agnostic (no {@code PasswordEncoder} dependency).
     */
    public static AppUser createFromGoogleSignUp(String username, String email, String fullName, String passwordHash) {
        AppUser appUser = new AppUser();
        OffsetDateTime now = OffsetDateTime.now();
        appUser.id = UUID.randomUUID();
        appUser.username = username;
        appUser.email = email;
        appUser.fullName = fullName;
        appUser.passwordHash = passwordHash;
        appUser.passwordChangeRequired = false;
        appUser.createdAt = now;
        appUser.updatedAt = now;
        return appUser;
    }
}
