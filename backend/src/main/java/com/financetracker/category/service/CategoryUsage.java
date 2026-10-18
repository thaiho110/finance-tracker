package com.financetracker.category.service;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the category module needs to know about, or do to, the transactions that use a category.
 * Implemented by the transaction module, so categories don't depend on transaction internals.
 */
public interface CategoryUsage {

    /** How many transactions (of any user) use the category. */
    long countUsing(UUID categoryId);

    /** Moves the user's transactions from one category to another; returns how many moved. */
    int reassign(UUID userId, UUID fromCategoryId, UUID toCategoryId);

    /** How many of the user's transactions in {@code fromCategoryId} have a merchant the predicate accepts. */
    int countMatching(UUID userId, UUID fromCategoryId, Predicate<String> merchantMatches);

    /** Moves those transactions to {@code toCategoryId}; returns how many moved. */
    int reassignMatching(UUID userId, UUID fromCategoryId, UUID toCategoryId, Predicate<String> merchantMatches);
}
