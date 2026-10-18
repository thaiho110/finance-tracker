package com.financetracker.admin.dto;

import com.financetracker.access.model.Role;
import com.financetracker.access.service.AccessService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A role and the permissions it grants")
public record RoleResponse(
    @Schema(example = "ADMIN") String code,
    String name,
    @Schema(description = "Higher rank may manage lower rank", example = "50") int rank,
    String description,
    @Schema(description = "False for SUPER_ADMIN, which always has every permission") boolean editable,
    List<String> permissions
) {
    public RoleResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public static RoleResponse from(Role role) {
        List<String> granted = AccessService.grantedBy(role).stream().map(Enum::name).sorted().toList();
        return new RoleResponse(role.getCode(), role.getName(), role.getRank(), role.getDescription(), !role.isSuperAdmin(), granted);
    }
}
