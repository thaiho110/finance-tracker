package com.financetracker.admin.api;

import com.financetracker.access.model.Permission;
import com.financetracker.admin.dto.AdminUserResponse;
import com.financetracker.admin.dto.AuditEntryResponse;
import com.financetracker.admin.dto.CreateUserRequest;
import com.financetracker.admin.dto.FeatureStateResponse;
import com.financetracker.admin.dto.PermissionResponse;
import com.financetracker.admin.dto.RoleResponse;
import com.financetracker.admin.dto.UpdateFeatureRequest;
import com.financetracker.admin.dto.UpdateRolePermissionsRequest;
import com.financetracker.admin.dto.UpdateUserRoleRequest;
import com.financetracker.admin.dto.UpdateUserStatusRequest;
import com.financetracker.admin.service.AuditService;
import com.financetracker.admin.service.RoleAdminService;
import com.financetracker.admin.service.UserAdminService;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Administration endpoints. Who may call each one is set per URL in {@code SecurityConfig} (ADR-018). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Accounts, roles, per-user features and the audit log")
public class AdminController {

    private final UserAdminService userAdminService;
    private final RoleAdminService roleAdminService;
    private final AuditService auditService;

    @GetMapping("/users")
    @Operation(summary = "List accounts (USER_READ)")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userAdminService.list(pageable));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get one account (USER_READ)")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.get(id));
    }

    @PostMapping("/users")
    @Operation(summary = "Create an account (USER_CREATE; roles other than USER need ROLE_MANAGE)")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.create(actor, request));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "Enable or disable an account with a lower role (USER_SUSPEND)")
    public ResponseEntity<AdminUserResponse> setStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateUserStatusRequest request,
                                                       @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(userAdminService.setEnabled(actor, id, request.enabled()));
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Change an account's role (ROLE_MANAGE)")
    public ResponseEntity<AdminUserResponse> changeRole(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateUserRoleRequest request,
                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(userAdminService.changeRole(actor, id, request.role()));
    }

    @GetMapping("/users/{id}/features")
    @Operation(summary = "Per-user features of an account (FEATURE_MANAGE)")
    public ResponseEntity<List<FeatureStateResponse>> features(@PathVariable UUID id) {
        return ResponseEntity.ok(userAdminService.features(id));
    }

    @PutMapping("/users/{id}/features/{permission}")
    @Operation(summary = "Switch a feature on or off for an account, or back to its role default (FEATURE_MANAGE)")
    public ResponseEntity<List<FeatureStateResponse>> setFeature(@PathVariable UUID id,
                                                                 @PathVariable Permission permission,
                                                                 @RequestBody UpdateFeatureRequest request,
                                                                 @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(userAdminService.setFeature(actor, id, permission, request.enabled()));
    }

    @GetMapping("/roles")
    @Operation(summary = "Roles and the permissions each grants (USER_READ)")
    public ResponseEntity<List<RoleResponse>> roles() {
        return ResponseEntity.ok(roleAdminService.roles());
    }

    @PutMapping("/roles/{code}/permissions")
    @Operation(summary = "Replace what a role may do (ROLE_MANAGE)")
    public ResponseEntity<RoleResponse> updateRolePermissions(@PathVariable String code,
                                                              @Valid @RequestBody UpdateRolePermissionsRequest request,
                                                              @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(roleAdminService.updatePermissions(actor, code, request.permissions()));
    }

    @GetMapping("/permissions")
    @Operation(summary = "The permission catalog (USER_READ)")
    public ResponseEntity<List<PermissionResponse>> permissions() {
        return ResponseEntity.ok(roleAdminService.catalog());
    }

    @GetMapping("/audit")
    @Operation(summary = "Audit log of administrative changes, newest first (AUDIT_READ)")
    public ResponseEntity<Page<AuditEntryResponse>> audit(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(auditService.list(pageable));
    }
}
