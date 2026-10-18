package com.financetracker.admin.service;

import com.financetracker.access.model.Permission;
import com.financetracker.access.model.Role;
import com.financetracker.access.service.AccessService;
import com.financetracker.admin.dto.PermissionResponse;
import com.financetracker.admin.dto.RoleResponse;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.auth.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** The role table as administrators see and edit it (ADR-018). */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final AccessService accessService;
    private final AuditService auditService;

    public List<RoleResponse> roles() {
        return accessService.roles().stream().map(RoleResponse::from).toList();
    }

    public List<PermissionResponse> catalog() {
        return Arrays.stream(Permission.values()).map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse updatePermissions(AuthenticatedUser actor, String roleCode, Collection<Permission> permissions) {
        Role role = accessService.replaceRolePermissions(roleCode, permissions);
        String granted = permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        auditService.record(actor.getId(), AuditAction.ROLE_PERMISSIONS_CHANGED, role.getCode(), granted);
        return RoleResponse.from(role);
    }
}
