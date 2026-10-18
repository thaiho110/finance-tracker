package com.financetracker.admin.service;

import com.financetracker.access.model.Permission;
import com.financetracker.access.model.Role;
import com.financetracker.access.service.AccessService;
import com.financetracker.admin.dto.AdminUserResponse;
import com.financetracker.admin.dto.CreateUserRequest;
import com.financetracker.admin.dto.FeatureStateResponse;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Account administration (ADR-018). URL rules in {@code SecurityConfig} decide who may call each
 * operation; this class adds the rules a URL can't express:
 * <ul>
 *   <li>nobody changes their own role or status here;</li>
 *   <li>you can only act on accounts with a lower role rank (the super admin can act on anyone else);</li>
 *   <li>at least one enabled super admin always remains;</li>
 *   <li>every change is written to the audit log.</li>
 * </ul>
 * Responses carry account data only, never transactions or amounts.
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessService accessService;
    private final TransactionService transactionService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return toResponse(find(userId));
    }

    /**
     * @throws AccessDeniedException    when a role other than USER is requested without ROLE_MANAGE
     * @throws IllegalArgumentException for an unknown role or an email that is already registered
     */
    @Transactional
    public AdminUserResponse create(AuthenticatedUser actor, CreateUserRequest request) {
        String role = request.role() == null || request.role().isBlank() ? Role.USER : accessService.requireRole(request.role());
        if (!Role.USER.equals(role) && !actor.hasPermission(Permission.ROLE_MANAGE)) {
            throw new AccessDeniedException("Creating a " + role + " account needs " + Permission.ROLE_MANAGE);
        }
        String email = request.email().strip();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(role)
            .build());
        auditService.record(actor.getId(), AuditAction.USER_CREATED, user.getEmail(), "role=" + role);
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse setEnabled(AuthenticatedUser actor, UUID userId, boolean enabled) {
        User target = find(userId);
        requireCanManage(actor, target);
        if (!enabled && Role.SUPER_ADMIN.equals(target.getRole())) {
            requireAnotherSuperAdmin(target);
        }
        if (target.isEnabled() != enabled) {
            target.setEnabled(enabled);
            userRepository.save(target);
            auditService.record(actor.getId(), enabled ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                target.getEmail(), null);
        }
        return toResponse(target);
    }

    @Transactional
    public AdminUserResponse changeRole(AuthenticatedUser actor, UUID userId, String roleCode) {
        User target = find(userId);
        requireCanManage(actor, target);
        String role = accessService.requireRole(roleCode);
        String previous = target.getRole();
        if (Role.SUPER_ADMIN.equals(previous) && !Role.SUPER_ADMIN.equals(role)) {
            requireAnotherSuperAdmin(target);
        }
        if (!role.equals(previous)) {
            target.setRole(role);
            userRepository.save(target);
            auditService.record(actor.getId(), AuditAction.ROLE_CHANGED, target.getEmail(), previous + " -> " + role);
        }
        return toResponse(target);
    }

    @Transactional(readOnly = true)
    public List<FeatureStateResponse> features(UUID userId) {
        User target = find(userId);
        return accessService.features(target.getId(), target.getRole()).stream().map(FeatureStateResponse::from).toList();
    }

    /** Switches a feature for one user; {@code enabled == null} returns it to the role's default. */
    @Transactional
    public List<FeatureStateResponse> setFeature(AuthenticatedUser actor, UUID userId, Permission permission, Boolean enabled) {
        User target = find(userId);
        accessService.setFeature(target.getId(), permission, enabled, actor.getId());
        String state = enabled == null ? "role default" : enabled ? "on" : "off";
        auditService.record(actor.getId(), AuditAction.FEATURE_CHANGED, target.getEmail(), permission + " " + state);
        return features(userId);
    }

    private User find(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private void requireCanManage(AuthenticatedUser actor, User target) {
        if (actor.getId().equals(target.getId())) {
            throw new AccessDeniedException("You can't change your own role or status");
        }
        boolean actorIsSuperAdmin = Role.SUPER_ADMIN.equals(actor.getRoleCode());
        if (!actorIsSuperAdmin && accessService.rank(actor.getRoleCode()) <= accessService.rank(target.getRole())) {
            throw new AccessDeniedException("You can only manage accounts with a lower role than yours");
        }
    }

    private void requireAnotherSuperAdmin(User target) {
        long enabledSuperAdmins = userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN);
        long others = enabledSuperAdmins - (target.isEnabled() ? 1 : 0);
        if (others < 1) {
            throw new IllegalArgumentException("There must always be at least one enabled super admin");
        }
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(),
            user.getCreatedAt(), user.getLastLoginAt(), transactionService.countFor(user.getId()));
    }
}
