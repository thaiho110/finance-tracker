package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The signed-in user")
public record UserInfoResponse(
    @Schema(description = "Stable user id. Use it (not the email, which can change) as the key for per-user visuals such as the avatar",
        example = "3f6c2a9e-8d1b-4c7a-9e2f-1b5d4a7c8e90")
    String id,

    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "User role", example = "user")
    String role
) { }
