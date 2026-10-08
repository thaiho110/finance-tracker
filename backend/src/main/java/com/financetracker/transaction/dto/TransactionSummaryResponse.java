package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Dashboard summary data")
public record TransactionSummaryResponse(
    @Schema(description = "Total expenses (negative amounts)") BigDecimal totalExpenses,
    @Schema(description = "Total income (positive amounts)") BigDecimal totalIncome,
    @Schema(description = "Total number of transactions") long transactionCount,
    @Schema(description = "Average transaction amount") BigDecimal averageTransaction,
    @Schema(description = "Spending breakdown by category") List<CategoryBreakdown> categoryBreakdown,
    @Schema(description = "Monthly spending trend") List<MonthlyTrend> monthlyTrend,
    @Schema(description = "Most recent transactions") List<TransactionResponse> recentTransactions
) {
    public record CategoryBreakdown(
        @Schema(description = "Category name") String category,
        @Schema(description = "Total amount for this category") BigDecimal total
    ) {}

    public record MonthlyTrend(
        @Schema(description = "Month in YYYY-MM format", example = "2024-01") String month,
        @Schema(description = "Total amount for this month") BigDecimal total
    ) {}
}
