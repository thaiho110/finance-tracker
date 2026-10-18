package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Authentication response with JWT tokens")
public record AuthResponse(
    @Schema(description = "JWT access token (24h expiry)", example = "eyJhbGciOiJIUzI1NiJ9...")
    String token,

    @Schema(description = "JWT refresh token (7d expiry)", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,

    @Schema(description = "Stable user id. Use it (not the email, which can change) as the key for per-user visuals such as the avatar",
        example = "3f6c2a9e-8d1b-4c7a-9e2f-1b5d4a7c8e90")
    String id,

    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "Role code (ADR-018)", example = "USER")
    String role,

    @Schema(description = "What this user may do right now; clients use it only to show or hide features",
        example = "[\"CSV_IMPORT\", \"TRANSACTION_READ\"]")
    List<String> permissions
) {
    public AuthResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
