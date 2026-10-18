package com.financetracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Keyword rule: merchants containing the keyword go to the category")
public record CategoryRuleResponse(
    UUID id,
    @Schema(example = "HIGHLANDS COFFEE") String keyword,
    UUID categoryId,
    @Schema(example = "Coffee Shop") String categoryName,
    boolean system
) { }
