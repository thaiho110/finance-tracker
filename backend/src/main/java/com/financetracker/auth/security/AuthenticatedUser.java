package com.financetracker.auth.security;

import com.financetracker.access.model.Permission;
import com.financetracker.auth.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in user as Spring Security sees it: the real {@code users.id}, the role, and the
 * effective permissions (ADR-018) as authorities. Handlers receive it through {@link CurrentUser};
 * nothing else should derive a user id.
 */
public final class AuthenticatedUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String roleCode;
    private final boolean enabled;
    private final Set<Permission> permissions;
    private final List<GrantedAuthority> authorities;

    /**
     * @param user        the account
     * @param permissions what the account may do right now (see {@code AccessService.effectivePermissions})
     */
    public AuthenticatedUser(User user, Set<Permission> permissions) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.roleCode = user.getRole();
        this.enabled = user.isEnabled();
        this.permissions = Set.copyOf(permissions);
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        this.permissions.forEach(permission -> granted.add(new SimpleGrantedAuthority(permission.name())));
        this.authorities = List.copyOf(granted);
    }

    /** The user's primary key in {@code users}; use it for {@code created_by} and ownership checks. */
    public UUID getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthenticatedUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[" + id + ", " + roleCode + "]";
    }
}
