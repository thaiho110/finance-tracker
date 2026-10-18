package com.financetracker.overview.dto;

import com.financetracker.budget.model.BudgetStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One month's overview (ADR-019): income, expenses and net, spending per expense category against its
 * budget and against last month, and the top spending merchants. Budget fields are present only when the
 * user has the BUDGETS feature; otherwise {@code budgetsEnabled} is false and every {@code budget}/{@code status}
 * is null. Transfers count in no total; a refund (negative amount) in an expense category lowers spending.
 */
@Schema(description = "A month's income, spending, budgets and top merchants")
public record OverviewResponse(
    @Schema(description = "The month shown", example = "2026-10") String month,
    BigDecimal income,
    BigDecimal expenses,
    @Schema(description = "income - expenses") BigDecimal net,
    BigDecimal previousIncome,
    BigDecimal previousExpenses,
    @Schema(description = "Whether this user has the BUDGETS feature") boolean budgetsEnabled,
    @Schema(nullable = true, description = "The total budget across all spending, or null if none is set") TotalBudget totalBudget,
    List<CategoryLine> categories,
    List<Merchant> topMerchants
) {
    public OverviewResponse {
        categories = Collections.unmodifiableList(categories);
        topMerchants = Collections.unmodifiableList(topMerchants);
    }

    public record TotalBudget(
        BigDecimal amount,
        BigDecimal spent,
        BudgetStatus status
    ) { }

    @Schema(description = "One expense category's spending this month")
    public record CategoryLine(
        UUID categoryId,
        String name,
        @Schema(nullable = true) String color,
        BigDecimal spent,
        BigDecimal previousSpent,
        @Schema(nullable = true, description = "The budget in force this month, or null if none") BigDecimal budget,
        @Schema(nullable = true, description = "OK, NEAR or OVER; null when there is no budget") BudgetStatus status
    ) { }

    public record Merchant(
        String merchant,
        BigDecimal total,
        long count
    ) { }
}
