package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dashboard summary data")
public record TransactionSummaryResponse(
    @Schema(description = "Total of expense categories (transfers excluded)") BigDecimal totalExpenses,
    @Schema(description = "Total of income categories") BigDecimal totalIncome,
    @Schema(description = "Total number of transactions") long transactionCount,
    @Schema(description = "Average transaction amount") BigDecimal averageTransaction,
    @Schema(description = "Spending breakdown by category") List<CategoryBreakdown> categoryBreakdown,
    @Schema(description = "Monthly spending trend") List<MonthlyTrend> monthlyTrend,
    @Schema(description = "Most recent transactions") List<TransactionResponse> recentTransactions
) {
    public TransactionSummaryResponse {
        categoryBreakdown = Collections.unmodifiableList(categoryBreakdown);
        monthlyTrend = Collections.unmodifiableList(monthlyTrend);
        recentTransactions = Collections.unmodifiableList(recentTransactions);
    }

    public record CategoryBreakdown(
        @Schema(description = "Category name") String category,
        @Schema(description = "Total amount for this category") BigDecimal total,
        @Schema(description = "Category id") UUID categoryId,
        @Schema(description = "EXPENSE or INCOME") String kind,
        @Schema(description = "Category color", nullable = true) String color
    ) { }

    public record MonthlyTrend(
        @Schema(description = "Month in YYYY-MM format", example = "2024-01") String month,
        @Schema(description = "Spending (expense categories) in this month") BigDecimal total
    ) { }
}
