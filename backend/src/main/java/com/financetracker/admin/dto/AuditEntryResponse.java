package com.financetracker.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One administrative change")
public record AuditEntryResponse(
    UUID id,
    @Schema(nullable = true, description = "Null when the application made the change itself") UUID actorId,
    @Schema(nullable = true) String actorEmail,
    @Schema(example = "USER_DISABLED") String action,
    @Schema(example = "someone@example.com") String target,
    @Schema(nullable = true) String details,
    Instant createdAt
) { }
