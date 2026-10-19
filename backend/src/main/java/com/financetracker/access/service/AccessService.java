package com.financetracker.access.service;

import com.financetracker.access.config.AccessProperties;
import com.financetracker.access.model.FeatureState;
import com.financetracker.access.model.Module;
import com.financetracker.access.model.ModuleState;
import com.financetracker.access.model.Permission;
import com.financetracker.access.model.PermissionKind;
import com.financetracker.access.model.Role;
import com.financetracker.access.model.UserFeatureOverride;
import com.financetracker.access.model.UserModulePreference;
import com.financetracker.access.repository.RoleRepository;
import com.financetracker.access.repository.UserFeatureOverrideRepository;
import com.financetracker.access.repository.UserModulePreferenceRepository;
import com.financetracker.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The role table and per-user features (ADR-018): what each account may do, and the changes the
 * super admin makes to that. Effective permissions are
 * {@code SUPER_ADMIN → all}, otherwise {@code role permissions + features switched on − features switched off}.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final RoleRepository roleRepository;
    private final UserFeatureOverrideRepository overrides;
    private final UserModulePreferenceRepository modulePreferences;
    private final AccessProperties properties;

    /** Role for a newly registered account: SUPER_ADMIN for {@code APP_SUPER_ADMIN_EMAIL}, otherwise USER. */
    public String initialRoleFor(String email) {
        return isConfiguredOwner(email) ? Role.SUPER_ADMIN : Role.USER;
    }

    /** Whether this email is the configured owner ({@code APP_SUPER_ADMIN_EMAIL}), who may always bootstrap. */
    public boolean isConfiguredOwner(String email) {
        String owner = properties.getSuperAdminEmail();
        return owner != null && !owner.isBlank() && email != null && owner.strip().equalsIgnoreCase(email.strip());
    }

    /**
     * Everything this account may do right now:
     * {@code SUPER_ADMIN → all}, otherwise
     * {@code role permissions + features on − features off − permissions of modules the user turned off} (ADR-020 §4).
     * A module the user turns off only ever narrows; it can never grant a permission the role or admin didn't give.
     */
    @Transactional(readOnly = true)
    public Set<Permission> effectivePermissions(UUID userId, String roleCode) {
        if (Role.SUPER_ADMIN.equals(roleCode)) {
            return Collections.unmodifiableSet(EnumSet.allOf(Permission.class));
        }
        Set<Permission> permissions = rolePlusFeatures(userId, roleCode);
        for (Module module : disabledModules(userId)) {
            permissions.removeAll(module.permissions());
        }
        return Collections.unmodifiableSet(permissions);
    }

    /** Role permissions with the per-user feature overrides applied, before any module choice (ADR-018). */
    private Set<Permission> rolePlusFeatures(UUID userId, String roleCode) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        permissions.addAll(rolePermissions(roleCode));
        for (UserFeatureOverride override : overrides.findByUserId(userId)) {
            if (override.getPermission().kind() != PermissionKind.FEATURE) {
                continue;
            }
            if (override.isEnabled()) {
                permissions.add(override.getPermission());
            } else {
                permissions.remove(override.getPermission());
            }
        }
        return permissions;
    }

    private Set<Module> disabledModules(UUID userId) {
        Set<Module> disabled = EnumSet.noneOf(Module.class);
        for (UserModulePreference preference : modulePreferences.findByUserId(userId)) {
            if (!preference.isEnabled()) {
                disabled.add(preference.getModule());
            }
        }
        return disabled;
    }

    /** Each module's state for this user (ADR-020 §4): code, the user's on/off choice, and whether it is available. */
    @Transactional(readOnly = true)
    public List<ModuleState> modules(UUID userId, String roleCode) {
        boolean superAdmin = Role.SUPER_ADMIN.equals(roleCode);
        Set<Permission> base = superAdmin ? EnumSet.allOf(Permission.class) : rolePlusFeatures(userId, roleCode);
        Map<Module, Boolean> chosen = modulePreferences.findByUserId(userId).stream()
            .collect(Collectors.toMap(UserModulePreference::getModule, UserModulePreference::isEnabled));
        return Arrays.stream(Module.values())
            .map(module -> {
                boolean available = superAdmin || module.permissions().stream().anyMatch(base::contains);
                boolean enabled = chosen.getOrDefault(module, Boolean.TRUE);
                return new ModuleState(module.name(), enabled, available);
            })
            .toList();
    }

    /**
     * Turns a module on or off for one user (ADR-020 §4). At least one available module must stay on, so the
     * change is rejected if it would leave the user with none.
     *
     * @throws IllegalArgumentException when it would turn off the user's last available module
     */
    @Transactional
    public void setModule(UUID userId, String roleCode, Module module, boolean enabled) {
        UserModulePreference preference = modulePreferences.findByUserIdAndModule(userId, module)
            .orElseGet(() -> new UserModulePreference(userId, module, true));
        preference.setEnabled(enabled);
        modulePreferences.save(preference);
        boolean anyLeftOn = modules(userId, roleCode).stream()
            .anyMatch(state -> state.available() && state.enabled());
        if (!anyLeftOn) {
            throw new IllegalArgumentException("At least one module must stay on");
        }
    }

    /** Every per-user feature for this account: what the role grants, any override, and the result. */
    @Transactional(readOnly = true)
    public List<FeatureState> features(UUID userId, String roleCode) {
        boolean superAdmin = Role.SUPER_ADMIN.equals(roleCode);
        Set<Permission> fromRole = superAdmin ? EnumSet.allOf(Permission.class) : rolePermissions(roleCode);
        Map<Permission, Boolean> switched = overrides.findByUserId(userId).stream()
            .collect(Collectors.toMap(UserFeatureOverride::getPermission, UserFeatureOverride::isEnabled));
        return Arrays.stream(Permission.values())
            .filter(permission -> permission.kind() == PermissionKind.FEATURE)
            .map(permission -> {
                boolean roleDefault = fromRole.contains(permission);
                Boolean override = superAdmin ? null : switched.get(permission);
                return new FeatureState(permission, roleDefault, override, override == null ? roleDefault : override);
            })
            .toList();
    }

    /**
     * Switches a feature on or off for one user, or back to their role's default when {@code enabled} is null.
     *
     * @throws IllegalArgumentException when the permission is not a per-user feature
     */
    @Transactional
    public void setFeature(UUID userId, Permission permission, Boolean enabled, UUID changedBy) {
        if (permission.kind() != PermissionKind.FEATURE) {
            throw new IllegalArgumentException(permission + " is not a per-user feature");
        }
        Optional<UserFeatureOverride> existing = overrides.findByUserIdAndPermission(userId, permission);
        if (enabled == null) {
            existing.ifPresent(overrides::delete);
        } else if (existing.isPresent()) {
            existing.get().change(enabled, changedBy);
            overrides.save(existing.get());
        } else {
            overrides.save(new UserFeatureOverride(userId, permission, enabled, changedBy));
        }
    }

    /** All roles, lowest rank first. */
    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roleRepository.findAllByOrderByRankAsc();
    }

    /** @throws EntityNotFoundException when there is no such role */
    @Transactional(readOnly = true)
    public Role role(String code) {
        return roleRepository.findById(normalize(code))
            .orElseThrow(() -> new EntityNotFoundException("Role not found: " + code));
    }

    /** The role code in canonical form ("admin" → "ADMIN"). @throws IllegalArgumentException for an unknown role */
    @Transactional(readOnly = true)
    public String requireRole(String code) {
        String normalized = normalize(code);
        if (!roleRepository.existsById(normalized)) {
            throw new IllegalArgumentException("Unknown role: " + code);
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public int rank(String roleCode) {
        return role(roleCode).getRank();
    }

    /**
     * Replaces what a role may do.
     *
     * @throws IllegalArgumentException for SUPER_ADMIN (always everything) or when an owner-only permission is included
     */
    @Transactional
    public Role replaceRolePermissions(String code, Collection<Permission> permissions) {
        Role role = role(code);
        if (role.isSuperAdmin()) {
            throw new IllegalArgumentException("The super admin always has every permission");
        }
        List<Permission> ownerOnly = permissions.stream().filter(p -> p.kind() == PermissionKind.OWNER).toList();
        if (!ownerOnly.isEmpty()) {
            throw new IllegalArgumentException("Only the super admin can hold " + ownerOnly);
        }
        role.replacePermissions(permissions);
        return roleRepository.save(role);
    }

    /** What a role grants: its stored permissions, or the whole catalog for SUPER_ADMIN. */
    public static Set<Permission> grantedBy(Role role) {
        return role.isSuperAdmin() ? EnumSet.allOf(Permission.class) : role.getPermissions();
    }

    private Set<Permission> rolePermissions(String roleCode) {
        return roleRepository.findById(normalize(roleCode)).map(Role::getPermissions).orElse(Set.of());
    }

    private static String normalize(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }
}
