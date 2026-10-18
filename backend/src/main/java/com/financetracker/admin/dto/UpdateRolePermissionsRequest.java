package com.financetracker.admin.dto;

import com.financetracker.access.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@Schema(description = "The complete set of permissions a role should grant")
public record UpdateRolePermissionsRequest(@NotNull Set<Permission> permissions) {
    public UpdateRolePermissionsRequest {
        permissions = permissions == null ? null : Set.copyOf(permissions);
    }
}
