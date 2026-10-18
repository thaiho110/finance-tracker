package com.financetracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Create or replace the rule for a keyword")
public record CategoryRuleRequest(
    @Schema(example = "Highlands Coffee") @NotBlank @Size(max = 255) String keyword,
    @NotNull UUID categoryId,
    @Schema(description = "Also move past transactions still in Other whose merchant matches; default false", nullable = true)
    Boolean applyToPast
) {
    public boolean shouldApplyToPast() {
        return Boolean.TRUE.equals(applyToPast);
    }
}
