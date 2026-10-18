package com.financetracker.budget.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sets a budget from a month on (ADR-019). {@code categoryId} null is the total budget. {@code amount} null
 * or 0 ends the budget. {@code month} null means the current month (Asia/Ho_Chi_Minh).
 */
@Schema(description = "Set or clear a budget for a category (or the total) from a month on")
public record BudgetSetRequest(
    @Schema(nullable = true, description = "The expense category, or null for the total budget") UUID categoryId,
    @Schema(nullable = true, description = "The monthly amount in VND; null or 0 ends the budget") BigDecimal amount,
    @Schema(nullable = true, example = "2026-10", description = "The month it starts from (YYYY-MM); null means this month") String month
) { }
