package com.financetracker.transaction.service;

import com.financetracker.category.service.CategoryUsage;
import com.financetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Lets the category module count and move transactions without knowing how they are stored. */
@Component
@RequiredArgsConstructor
public class TransactionCategoryUsage implements CategoryUsage {

    private final TransactionRepository transactions;

    @Override
    public long countUsing(UUID categoryId) {
        return transactions.countByCategoryId(categoryId);
    }

    @Override
    @Transactional
    public int reassign(UUID userId, UUID fromCategoryId, UUID toCategoryId) {
        return transactions.reassignCategory(userId, fromCategoryId, toCategoryId);
    }

    @Override
    public int countMatching(UUID userId, UUID fromCategoryId, Predicate<String> merchantMatches) {
        return matching(userId, fromCategoryId, merchantMatches).size();
    }

    @Override
    @Transactional
    public int reassignMatching(UUID userId, UUID fromCategoryId, UUID toCategoryId, Predicate<String> merchantMatches) {
        List<UUID> ids = matching(userId, fromCategoryId, merchantMatches);
        return ids.isEmpty() ? 0 : transactions.setCategory(userId, ids, toCategoryId);
    }

    /** A transaction matches when its clean merchant or its raw description does. */
    private List<UUID> matching(UUID userId, UUID categoryId, Predicate<String> merchantMatches) {
        return transactions.findMerchantsInCategory(userId, categoryId).stream()
            .filter(row -> merchantMatches.test((String) row[1]) || merchantMatches.test((String) row[2]))
            .map(row -> (UUID) row[0])
            .toList();
    }
}
