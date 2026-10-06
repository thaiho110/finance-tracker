package com.financetracker.category.service;

import com.financetracker.category.model.CategoryMapping;
import com.financetracker.category.repository.CategoryMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorizationService Unit Tests")
class CategorizationServiceTest {

    @Mock private CategoryMappingRepository repository;

    @InjectMocks private CategorizationService categorizationService;

    @BeforeEach
    void setUp() {
        List<CategoryMapping> mappings = List.of(
            CategoryMapping.builder().keyword("STARBUCKS").category("Coffee Shop").build(),
            CategoryMapping.builder().keyword("AMAZON").category("Online Shopping").build(),
            CategoryMapping.builder().keyword("UBER").category("Transportation").build(),
            CategoryMapping.builder().keyword("MCDONALDS").category("Fast Food").build()
        );
        when(repository.findAll()).thenReturn(mappings);
        categorizationService.refreshCache();
    }

    @Test
    @DisplayName("Should categorize known merchant")
    void shouldCategorizeKnownMerchant() {
        assertThat(categorizationService.categorize("STARBUCKS")).isEqualTo("Coffee Shop");
        assertThat(categorizationService.categorize("AMAZON")).isEqualTo("Online Shopping");
        assertThat(categorizationService.categorize("UBER")).isEqualTo("Transportation");
        assertThat(categorizationService.categorize("MCDONALDS")).isEqualTo("Fast Food");
    }

    @Test
    @DisplayName("Should return 'Other' for unknown merchant")
    void shouldReturnOtherForUnknown() {
        assertThat(categorizationService.categorize("UNKNOWN_STORE")).isEqualTo("Other");
    }

    @Test
    @DisplayName("Should return 'Other' for empty input")
    void shouldReturnOtherForEmpty() {
        assertThat(categorizationService.categorize("")).isEqualTo("Other");
        assertThat(categorizationService.categorize(null)).isEqualTo("Other");
        assertThat(categorizationService.categorize("   ")).isEqualTo("Other");
    }

    @Test
    @DisplayName("Should clean merchant name by removing store numbers")
    void shouldRemoveStoreNumbers() {
        assertThat(categorizationService.cleanMerchant("STARBUCKS #12345")).isEqualTo("STARBUCKS");
    }

    @Test
    @DisplayName("Should clean merchant name by normalizing whitespace")
    void shouldNormalizeWhitespace() {
        assertThat(categorizationService.cleanMerchant("  AMAZON   PRIME  ")).isEqualTo("AMAZON PRIME");
    }

    @Test
    @DisplayName("Should categorize by partial match")
    void shouldCategorizeByPartialMatch() {
        assertThat(categorizationService.categorize("STARBUCKS COFFEE #456")).isEqualTo("Coffee Shop");
        assertThat(categorizationService.categorize("AMAZON WEB SERVICES")).isEqualTo("Online Shopping");
    }
}
