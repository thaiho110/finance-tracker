package com.financetracker.admin.dto;

import com.financetracker.auth.model.RegistrationInvite;

import java.time.Instant;
import java.util.UUID;

/** An invite as the admin list shows it; never includes the token (only its hash is stored). */
public record InviteResponse(
    UUID id,
    Instant createdAt,
    Instant expiresAt,
    boolean used,
    Instant usedAt
) {
    public static InviteResponse of(RegistrationInvite invite) {
        return new InviteResponse(invite.getId(), invite.getCreatedAt(), invite.getExpiresAt(),
            invite.isUsed(), invite.getUsedAt());
    }
}
