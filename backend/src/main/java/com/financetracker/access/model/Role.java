package com.financetracker.access.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A named set of permissions with a rank (ADR-018). Higher rank may manage lower rank.
 * {@link #SUPER_ADMIN} has every permission regardless of its stored set.
 */
@Entity
@Table(name = "roles")
public class Role {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int rank;

    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "permission", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // for JPA
    }

    public Role(String code, String name, int rank, Collection<Permission> permissions) {
        this.code = code;
        this.name = name;
        this.rank = rank;
        this.builtIn = true;
        this.permissions = new HashSet<>(permissions);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getRank() {
        return rank;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public boolean isSuperAdmin() {
        return SUPER_ADMIN.equals(code);
    }

    /** The stored permissions (a copy). For {@link #SUPER_ADMIN} use {@code AccessService}, which grants everything. */
    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }

    public void replacePermissions(Collection<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }
}
