package com.financetracker.auth.dto;

import com.financetracker.access.model.ModuleState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The signed-in user")
public record UserInfoResponse(
    @Schema(description = "Stable user id. Use it (not the email, which can change) as the key for per-user visuals such as the avatar",
        example = "3f6c2a9e-8d1b-4c7a-9e2f-1b5d4a7c8e90")
    String id,

    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "Role code (ADR-018)", example = "USER")
    String role,

    @Schema(description = "What this user may do right now; clients use it only to show or hide features",
        example = "[\"CSV_IMPORT\", \"TRANSACTION_READ\"]")
    List<String> permissions,

    @Schema(description = "The user's modules (ADR-020 §4); clients build navigation and the home screen from these")
    List<ModuleState> modules
) {
    public UserInfoResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}
