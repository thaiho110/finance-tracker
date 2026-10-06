package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response with JWT tokens")
public record AuthResponse(
    @Schema(description = "JWT access token (24h expiry)", example = "eyJhbGciOiJIUzI1NiJ9...")
    String token,

    @Schema(description = "JWT refresh token (7d expiry)", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,

    @Schema(description = "User email address", example = "user@example.com")
    String email,

    @Schema(description = "User role", example = "user")
    String role
) {}
