package com.financetracker.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** A new device API key to create for a device owned by {@code userId}. */
@Schema(description = "A device API key created by an administrator")
public record CreateDeviceKeyRequest(
    @Schema(description = "The account the device acts as; data the device writes belongs to this user")
    @NotNull UUID userId,

    @Schema(description = "A human label to recognise the key by", example = "Kitchen tablet")
    @NotBlank @Size(max = 100) String label
) { }
