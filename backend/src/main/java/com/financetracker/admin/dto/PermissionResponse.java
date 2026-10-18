package com.financetracker.admin.dto;

import com.financetracker.access.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An entry of the permission catalog")
public record PermissionResponse(
    @Schema(example = "CSV_IMPORT") String code,
    @Schema(example = "IMPORTS") String domain,
    @Schema(description = "ROLE, FEATURE (switchable per user) or OWNER (super admin only)", example = "FEATURE") String kind,
    String description
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.name(), permission.domain().name(), permission.kind().name(),
            permission.description());
    }
}
