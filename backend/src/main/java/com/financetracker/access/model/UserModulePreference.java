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

/**
 * A user's on/off choice for one {@link Module} (ADR-020 §4). No row means the module is on (the default).
 * A stored {@code enabled = false} subtracts the module's permissions from what the user can do.
 */
@Entity
@Table(name = "user_module_preferences")
public class UserModulePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Module module;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserModulePreference() {
        // for JPA
    }

    public UserModulePreference(UUID userId, Module module, boolean enabled) {
        this.userId = userId;
        this.module = module;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public Module getModule() {
        return module;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
