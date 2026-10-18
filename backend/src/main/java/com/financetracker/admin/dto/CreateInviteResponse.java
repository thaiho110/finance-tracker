package com.financetracker.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A freshly created invite. The token is shown only here — it is not stored and cannot be retrieved again. */
public record CreateInviteResponse(
    UUID id,
    @Schema(description = "The one-time invite token; put it in the registration link and send it by hand") String token,
    Instant expiresAt
) { }
