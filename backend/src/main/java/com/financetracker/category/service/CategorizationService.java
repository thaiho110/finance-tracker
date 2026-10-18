package com.financetracker.category.service;

import com.financetracker.category.model.Category;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.model.CategoryRule;
import com.financetracker.category.repository.CategoryRepository;
import com.financetracker.category.repository.CategoryRuleRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a merchant name into a category with keyword rules (ADR-015).
 *
 * <p>Callers pass the merchant exactly as it appears on a statement or receipt. Matching ignores case,
 * Vietnamese accents, store numbers and long ids, and a keyword must appear as a whole word ({@code GAS}
 * does not match {@code VEGAS}). The user's own rules are tried before system rules; within each group
 * the longest keyword wins. Rules pointing at an archived category, or at a system category the user
 * hid, are skipped. Nothing matched means the system category {@value Category#OTHER}.
 *
 * <p>Rules are cached per user; {@link CategoryService} evicts the cache on every change.
 */
@Service
public class CategorizationService {

    private static final Pattern STORE_NUMBER = Pattern.compile("#\\d+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{10,}\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final long CACHE_SIZE = 2_000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final Cache<UUID, List<CompiledRule>> userRules = Caffeine.newBuilder()
        .maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_TTL).build();
    private volatile List<CompiledRule> systemRules;
    private volatile CategoryRef other;

    public CategorizationService(CategoryRepository categories, CategoryRuleRepository rules) {
        this.categories = categories;
        this.rules = rules;
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
     * @return the category of the first matching rule (user rules first, longest keyword first), or Other
     */
    public CategoryRef categorize(UUID userId, String merchant) {
        String text = merchant == null ? "" : normalize(cleanMerchant(merchant));
        Set<UUID> hidden = categories.findHiddenIds(userId);
        for (CompiledRule rule : rulesFor(userId)) {
            if (!hidden.contains(rule.category().id()) && rule.pattern().matcher(text).find()) {
                return rule.category();
            }
        }
        return other();
    }

    /** The system category {@value Category#OTHER}. */
    public CategoryRef other() {
        CategoryRef current = other;
        if (current == null) {
            current = categories.findSystemByName(Category.OTHER).map(CategoryRef::of)
                .orElseThrow(() -> new IllegalStateException("System category 'Other' is missing (migration V5)"));
            other = current;
        }
        return current;
    }

    /** Merchant keywords known from the system rules, used to recognize the merchant on scanned receipts. */
    public List<String> knownMerchantKeywords() {
        return systemRules().stream().map(CompiledRule::keyword).toList();
    }

    /** Whether a merchant (as written) contains the keyword as a whole word, the way rules match. */
    public boolean matches(String keyword, String merchant) {
        String text = merchant == null ? "" : normalize(cleanMerchant(merchant));
        return wholeWord(normalize(keyword)).matcher(text).find();
    }

    /** Forget one user's cached rules after their rules or categories changed. */
    public void evictUser(UUID userId) {
        userRules.invalidate(userId);
    }

    /** Forget every cached rule after a system rule or category changed. */
    public void evictAll() {
        systemRules = null;
        other = null;
        userRules.invalidateAll();
    }

    /** Upper case, accents removed, Đ→D, single spaces: the form keywords and merchants are compared in. */
    public static String normalize(String text) {
        String withoutDStroke = text.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(withoutDStroke, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(withoutMarks.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private List<CompiledRule> rulesFor(UUID userId) {
        return userRules.get(userId, id -> {
            List<CompiledRule> all = new ArrayList<>(compile(rules.findByUserIdOrderByKeyword(id)));
            all.addAll(systemRules());
            return List.copyOf(all);
        });
    }

    private List<CompiledRule> systemRules() {
        List<CompiledRule> current = systemRules;
        if (current == null) {
            current = compile(rules.findSystem());
            systemRules = current;
        }
        return current;
    }

    /** Usable rules (category exists and isn't archived), longest keyword first. */
    private List<CompiledRule> compile(List<CategoryRule> source) {
        Map<UUID, Category> byId = categories.findByIdIn(source.stream().map(CategoryRule::getCategoryId).toList())
            .stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        return source.stream()
            .filter(rule -> rule.getKeyword() != null && !rule.getKeyword().isBlank())
            .filter(rule -> byId.containsKey(rule.getCategoryId()) && !byId.get(rule.getCategoryId()).isArchived())
            .map(rule -> {
                String key = normalize(rule.getKeyword());
                return new CompiledRule(rule.getKeyword(), key, CategoryRef.of(byId.get(rule.getCategoryId())), wholeWord(key));
            })
            .sorted(Comparator.comparingInt((CompiledRule rule) -> rule.normalizedKeyword().length()).reversed())
            .toList();
    }

    private static Pattern wholeWord(String normalizedKeyword) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedKeyword) + "(?![\\p{L}\\p{N}])");
    }

    private record CompiledRule(String keyword, String normalizedKeyword, CategoryRef category, Pattern pattern) { }
}
