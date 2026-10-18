package com.financetracker.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An account as administrators see it: no amounts or transactions, only counts")
public record AdminUserResponse(
    UUID id,
    String email,
    @Schema(example = "USER") String role,
    boolean enabled,
    Instant createdAt,
    @Schema(nullable = true) Instant lastLoginAt,
    @Schema(description = "Number of saved transactions") long transactionCount
) { }
