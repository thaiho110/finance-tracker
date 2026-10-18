package com.financetracker.category.model;

/**
 * How a category's transactions count (ADR-015, amended). The kind decides the bucket, not the sign of
 * the amount: a refund (negative amount) in an expense category lowers that category's spending.
 */
public enum CategoryKind {
    /** Money spent; counts toward spending and budgets. */
    EXPENSE,
    /** Money received. */
    INCOME,
    /** Money moved between the user's own accounts; left out of every total. */
    TRANSFER
}
