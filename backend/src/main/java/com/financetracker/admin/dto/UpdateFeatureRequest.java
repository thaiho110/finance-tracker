package com.financetracker.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateFeatureRequest(
    @Schema(description = "true = on for this user, false = off, null = follow the role", nullable = true)
    Boolean enabled
) { }
