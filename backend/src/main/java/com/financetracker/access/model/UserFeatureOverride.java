package com.financetracker.access.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A {@link PermissionKind#FEATURE} switched on or off for one user, overriding their role. */
@Entity
@Table(name = "user_feature_overrides")
public class UserFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected UserFeatureOverride() {
        // for JPA
    }

    public UserFeatureOverride(UUID userId, Permission permission, boolean enabled, UUID updatedBy) {
        this.userId = userId;
        this.permission = permission;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void change(boolean newEnabled, UUID changedBy) {
        this.enabled = newEnabled;
        this.updatedBy = changedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public Permission getPermission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
