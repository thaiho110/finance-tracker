package com.financetracker.admin.dto;

import com.financetracker.access.model.FeatureState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A per-user feature and why it is on or off")
public record FeatureStateResponse(
    @Schema(example = "RECEIPT_SCAN") String permission,
    @Schema(example = "IMPORTS") String domain,
    String description,
    @Schema(description = "Whether the feature is on for this user") boolean enabled,
    @Schema(description = "Whether the user's role grants it") boolean roleDefault,
    @Schema(description = "Override for this user; null when following the role", nullable = true) Boolean override
) {
    public static FeatureStateResponse from(FeatureState state) {
        return new FeatureStateResponse(state.permission().name(), state.permission().domain().name(),
            state.permission().description(), state.enabled(), state.roleDefault(), state.override());
    }
}
