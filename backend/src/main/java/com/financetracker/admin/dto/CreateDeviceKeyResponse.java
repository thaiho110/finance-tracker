package com.financetracker.admin.dto;

import com.financetracker.auth.service.DeviceApiKeyService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** A freshly created device key. The key is shown only here — it is not stored and cannot be retrieved again. */
public record CreateDeviceKeyResponse(
    UUID id,
    @Schema(description = "The one-time device key; put it in the device's X-Api-Key header") String key,
    UUID userId,
    String ownerEmail,
    String label
) {
    public static CreateDeviceKeyResponse of(DeviceApiKeyService.Created created) {
        return new CreateDeviceKeyResponse(created.id(), created.key(), created.userId(),
            created.ownerEmail(), created.label());
    }
}
