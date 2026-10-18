package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The signed-in user")
public record UserInfoResponse(
    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "User role", example = "user")
    String role
) { }
