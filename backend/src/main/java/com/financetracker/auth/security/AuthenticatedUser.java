package com.financetracker.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The signed-in user as Spring Security sees it, carrying the real {@code users.id}.
 * Handlers receive it through {@link CurrentUser}; nothing else should derive a user id.
 */
public final class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(UUID id, String email, String passwordHash, String role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
    }

    /** The user's primary key in {@code users}; use it for {@code created_by} and ownership checks. */
    public UUID getId() {
        return id;
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
    public boolean equals(Object other) {
        return other instanceof AuthenticatedUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[" + id + "]";
    }
}
