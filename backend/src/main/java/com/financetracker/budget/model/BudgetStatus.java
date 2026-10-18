package com.financetracker.budget.model;

import java.math.BigDecimal;

/**
 * How a month's spending sits against its budget (ADR-019). Computed on the server so every client
 * shows the same warning: amber at ≥ 80 %, red at ≥ 100 %.
 */
public enum BudgetStatus {
    OK,
    NEAR,
    OVER;

    /** The share of the budget, in percent, at which the warning turns amber. */
    private static final BigDecimal NEAR_NUMERATOR = BigDecimal.valueOf(80);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * The status of {@code spent} against {@code budget}. A refund can push spending negative, which is
     * {@link #OK}. {@code budget} is the amount in force and is always positive here (0 means no budget).
     */
    public static BudgetStatus of(BigDecimal spent, BigDecimal budget) {
        if (spent.compareTo(budget) >= 0) {
            return OVER;
        }
        // NEAR from 80 % on: spent * 100 >= budget * 80, kept in integers to avoid rounding at the boundary.
        if (spent.multiply(HUNDRED).compareTo(budget.multiply(NEAR_NUMERATOR)) >= 0) {
            return NEAR;
        }
        return OK;
    }
}
