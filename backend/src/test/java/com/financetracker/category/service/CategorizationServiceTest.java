package com.financetracker.category.service;

import com.financetracker.category.model.Category;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRule;
import com.financetracker.category.repository.CategoryRepository;
import com.financetracker.category.repository.CategoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategorizationService")
class CategorizationServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private CategoryRepository categories;
    @Mock private CategoryRuleRepository rules;

    private CategorizationService service;
    private final Map<String, Category> byName = new HashMap<>();
    private final List<CategoryRule> systemRules = new ArrayList<>();
    private final List<CategoryRule> userRules = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CategorizationService(categories, rules);
        system("Other");
        systemRule("STARBUCKS", system("Coffee Shop"));
        systemRule("PHUC LONG", byName.get("Coffee Shop"));
        systemRule("AMAZON", system("Online Shopping"));
        systemRule("UBER", system("Transportation"));
        systemRule("GAS", byName.get("Transportation"));
        systemRule("TARGET", system("Shopping"));
        systemRule("TARGET OPTICAL", system("Health"));

        when(categories.findSystemByName("Other")).thenReturn(Optional.of(byName.get("Other")));
        when(categories.findByIdIn(any())).thenAnswer(inv -> List.copyOf(byName.values()));
        when(categories.findHiddenIds(any())).thenReturn(Set.of());
        when(rules.findSystem()).thenReturn(systemRules);
        when(rules.findByUserIdOrderByKeyword(USER)).thenReturn(userRules);
    }

    private Category system(String name) {
        Category category = Category.builder().id(UUID.randomUUID()).name(name).kind(CategoryKind.EXPENSE).build();
        byName.put(name, category);
        return category;
    }

    private void systemRule(String keyword, Category category) {
        systemRules.add(CategoryRule.builder().id(UUID.randomUUID()).keyword(keyword).categoryId(category.getId()).build());
    }

    private String categorize(String merchant) {
        return service.categorize(USER, merchant).name();
    }

    @Test
    @DisplayName("finds the category of a known merchant, and Other otherwise")
    void knownAndUnknownMerchants() {
        assertThat(categorize("STARBUCKS")).isEqualTo("Coffee Shop");
        assertThat(categorize("AMAZON WEB SERVICES")).isEqualTo("Online Shopping");
        assertThat(categorize("UNKNOWN_STORE")).isEqualTo("Other");
        assertThat(categorize("")).isEqualTo("Other");
        assertThat(categorize(null)).isEqualTo("Other");
        assertThat(categorize("   ")).isEqualTo("Other");
    }

    @Test
    @DisplayName("cleans store numbers and extra whitespace from the merchant")
    void cleansMerchant() {
        assertThat(service.cleanMerchant("STARBUCKS #12345")).isEqualTo("STARBUCKS");
        assertThat(service.cleanMerchant("  AMAZON   PRIME  ")).isEqualTo("AMAZON PRIME");
    }

    @Test
    @DisplayName("prefers the longest matching keyword")
    void longestKeywordWins() {
        assertThat(categorize("TARGET OPTICAL #88")).isEqualTo("Health");
        assertThat(categorize("TARGET STORE")).isEqualTo("Shopping");
    }

    @Test
    @DisplayName("matches keywords only as whole words")
    void wholeWordsOnly() {
        assertThat(categorize("VEGAS BAR")).isEqualTo("Other");
        assertThat(categorize("SHELL GAS STATION")).isEqualTo("Transportation");
    }

    @Test
    @DisplayName("ignores case and Vietnamese accents")
    void ignoresCaseAndAccents() {
        assertThat(categorize("Phúc Long Coffee & Tea")).isEqualTo("Coffee Shop");
        assertThat(categorize("phuc long")).isEqualTo("Coffee Shop");
        assertThat(categorize("starbucks #12345")).isEqualTo("Coffee Shop");
    }

    @Test
    @DisplayName("a user's rule beats a system rule, even a longer one")
    void userRuleWins() {
        Category pets = Category.builder().id(UUID.randomUUID()).name("Pets").userId(USER).build();
        byName.put("Pets", pets);
        userRules.add(CategoryRule.builder().userId(USER).keyword("TARGET").categoryId(pets.getId()).build());

        assertThat(categorize("TARGET OPTICAL")).isEqualTo("Pets");
    }

    @Test
    @DisplayName("skips rules whose category is archived or hidden by the user")
    void skipsArchivedAndHidden() {
        byName.get("Online Shopping").setArchived(true);
        when(categories.findHiddenIds(USER)).thenReturn(Set.of(byName.get("Transportation").getId()));

        assertThat(categorize("AMAZON")).isEqualTo("Other");
        assertThat(categorize("UBER")).isEqualTo("Other");
        assertThat(categorize("STARBUCKS")).isEqualTo("Coffee Shop");
    }

    @Test
    @DisplayName("caches rules per user until evicted")
    void cachesUntilEvicted() {
        categorize("STARBUCKS");
        categorize("UBER");
        verify(rules, times(1)).findByUserIdOrderByKeyword(USER);

        service.evictUser(USER);
        categorize("STARBUCKS");
        verify(rules, times(2)).findByUserIdOrderByKeyword(USER);
        verify(rules, never()).findSystemByKeyword(anyString());
    }

    @Test
    @DisplayName("matches() follows the same whole-word, accent-free rules")
    void matchesHelper() {
        assertThat(service.matches("highlands coffee", "HIGHLANDS COFFEE #12 Q1")).isTrue();
        assertThat(service.matches("GAS", "VEGAS")).isFalse();
        assertThat(service.matches("PHUC LONG", "Phúc Long")).isTrue();
    }
}
