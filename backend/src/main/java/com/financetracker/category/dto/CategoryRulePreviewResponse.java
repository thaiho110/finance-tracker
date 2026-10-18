package com.financetracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many past transactions in Other a keyword would move")
public record CategoryRulePreviewResponse(
    @Schema(example = "HIGHLANDS COFFEE") String keyword,
    int matching
) { }
