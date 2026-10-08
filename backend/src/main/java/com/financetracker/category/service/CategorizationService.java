package com.financetracker.category.service;

import com.financetracker.category.model.CategoryMapping;
import com.financetracker.category.repository.CategoryMappingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DB-backed categorization service.
 * Merchant keywords and their categories are stored in the category_mappings table
 * and cached on startup for fast matching. The frontend can query categories via
 * the CategoryController API.
 */
@Service
@RequiredArgsConstructor
public class CategorizationService {

    private final CategoryMappingRepository repository;

    // In-memory cache refreshed on startup
    private final List<CachedMapping> cache = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void refreshCache() {
        List<CategoryMapping> mappings = repository.findAll();
        cache.clear();
        for (CategoryMapping mapping : mappings) {
            cache.add(new CachedMapping(mapping.getKeyword(), mapping.getCategory()));
        }
    }

    /**
     * Clean a raw merchant string by removing store numbers, transaction IDs,
     * and normalizing whitespace.
     */
    public String cleanMerchant(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toUpperCase()
            .replaceAll("#\\d+", "")
            .replaceAll("\\b\\d{10,}\\b", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Categorize a cleaned merchant name by matching against the DB-backed keyword map.
     * Matches longest keywords first to avoid false positives (e.g., "MCDONALDS" before "MCDONALD'S").
     */
    public String categorize(String cleanedMerchant) {
        if (cleanedMerchant == null || cleanedMerchant.isBlank()) {
            return "Other";
        }

        for (CachedMapping mapping : cache) {
            if (cleanedMerchant.contains(mapping.keyword())) {
                return mapping.category();
            }
        }
        return "Other";
    }

    private record CachedMapping(String keyword, String category) {}
}
