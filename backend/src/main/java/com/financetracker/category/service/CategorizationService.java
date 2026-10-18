package com.financetracker.category.service;

import com.financetracker.category.model.CategoryMapping;
import com.financetracker.category.repository.CategoryMappingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a merchant name into a category using the keyword table ({@code category_mappings}).
 *
 * <p>Callers pass the merchant exactly as it appears on a statement or receipt. Matching ignores
 * case, Vietnamese accents, store numbers and long ids; a keyword must appear as a whole word
 * ({@code GAS} does not match {@code VEGAS}); when several keywords match, the longest wins.
 * Mappings are cached at startup; call {@link #refreshCache()} after changing them.
 */
@Service
@RequiredArgsConstructor
public class CategorizationService {

    /** Category for merchants that match no keyword. */
    public static final String DEFAULT_CATEGORY = "Other";

    private static final Pattern STORE_NUMBER = Pattern.compile("#\\d+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{10,}\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private final CategoryMappingRepository repository;

    /** Longest keyword first, so "TARGET OPTICAL" is tried before "TARGET". Replaced as a whole on refresh. */
    private volatile List<CachedMapping> cache = List.of();

    @PostConstruct
    public void refreshCache() {
        cache = repository.findAll().stream()
            .filter(mapping -> mapping.getKeyword() != null && !mapping.getKeyword().isBlank())
            .map(CategorizationService::toCached)
            .sorted(Comparator.comparingInt((CachedMapping mapping) -> mapping.normalizedKeyword().length()).reversed())
            .toList();
    }

    /**
     * Display form of a merchant: upper case, store numbers ("#12345") and long transaction ids removed,
     * whitespace collapsed. Accents are kept.
     */
    public String cleanMerchant(String raw) {
        String upper = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        String withoutIds = LONG_NUMBER.matcher(STORE_NUMBER.matcher(upper).replaceAll("")).replaceAll("");
        return WHITESPACE.matcher(withoutIds).replaceAll(" ").trim();
    }

    /**
     * Category for a merchant as written on the source ("STARBUCKS #12345", "Phúc Long Coffee").
     *
     * @return the category of the longest whole-word keyword match, or {@value #DEFAULT_CATEGORY}
     */
    public String categorize(String merchant) {
        String text = merchant == null ? "" : normalize(cleanMerchant(merchant));
        return cache.stream()
            .filter(mapping -> mapping.pattern().matcher(text).find())
            .map(CachedMapping::category)
            .findFirst()
            .orElse(DEFAULT_CATEGORY);
    }

    /** Merchant keywords known from the mappings, used to recognize the merchant on scanned receipts. */
    public List<String> knownMerchantKeywords() {
        return cache.stream().map(CachedMapping::keyword).toList();
    }

    /** Upper case, accents removed, Đ→D, single spaces: the form keywords and merchants are compared in. */
    static String normalize(String text) {
        String withoutDStroke = text.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(withoutDStroke, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(withoutMarks.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private static CachedMapping toCached(CategoryMapping mapping) {
        String key = normalize(mapping.getKeyword());
        Pattern wholeWord = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(key) + "(?![\\p{L}\\p{N}])");
        return new CachedMapping(mapping.getKeyword(), key, mapping.getCategory(), wholeWord);
    }

    private record CachedMapping(String keyword, String normalizedKeyword, String category, Pattern pattern) { }
}
