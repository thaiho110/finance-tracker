package com.financetracker.admin.dto;

import com.financetracker.auth.service.DeviceApiKeyService;

import java.time.Instant;
import java.util.UUID;

/** A device key as the admin list shows it; never includes the raw key (only its hash is stored). */
public record DeviceKeyResponse(
    UUID id,
    String label,
    UUID userId,
    String ownerEmail,
    Instant createdAt,
    Instant lastUsedAt,
    boolean revoked,
    Instant revokedAt
) {
    public static DeviceKeyResponse of(DeviceApiKeyService.Summary summary) {
        return new DeviceKeyResponse(summary.id(), summary.label(), summary.userId(), summary.ownerEmail(),
            summary.createdAt(), summary.lastUsedAt(), summary.revokedAt() != null, summary.revokedAt());
    }
}
