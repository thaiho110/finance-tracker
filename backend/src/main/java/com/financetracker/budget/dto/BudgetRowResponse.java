package com.financetracker.budget.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One editable row on the Budgets page (ADR-019): an expense category (or the total, with {@code categoryId}
 * null) with its budget in force this month and what was spent this month and last month.
 */
@Schema(description = "A budget row: a category (or the total) with its amount and recent spending")
public record BudgetRowResponse(
    @Schema(nullable = true, description = "The category, or null for the total budget") UUID categoryId,
    String name,
    @Schema(nullable = true) String color,
    @Schema(description = "The budget in force this month; 0 means no budget") BigDecimal amount,
    BigDecimal spent,
    BigDecimal previousSpent
) { }
