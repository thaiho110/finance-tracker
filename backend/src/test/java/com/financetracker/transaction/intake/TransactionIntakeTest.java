package com.financetracker.transaction.intake;

import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.service.CategorizationService;
import com.financetracker.transaction.service.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionIntake Unit Tests")
class TransactionIntakeTest {

    private static final LocalDate SEP_5 = LocalDate.of(2026, 9, 5);

    @Mock private CategorizationService categorizationService;
    @Mock private DeduplicationService deduplicationService;

    @InjectMocks private TransactionIntake intake;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(categorizationService.cleanMerchant(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(categorizationService.categorize(any(), any())).thenReturn(new CategoryRef(java.util.UUID.nameUUIDFromBytes("Other".getBytes()), "Other", CategoryKind.EXPENSE, null));
    }

    @Test
    @DisplayName("Should categorize from the description as written")
    void shouldCategorize() {
        when(categorizationService.cleanMerchant("STARBUCKS #12345")).thenReturn("STARBUCKS");
        when(categorizationService.categorize(userId, "STARBUCKS #12345")).thenReturn(new CategoryRef(java.util.UUID.nameUUIDFromBytes("Coffee Shop".getBytes()), "Coffee Shop", CategoryKind.EXPENSE, null));

        DraftTransaction draft = intake.draft(userId, new RawEntry(SEP_5, "STARBUCKS #12345", new BigDecimal("12.50")));

        assertThat(draft.rawDescription()).isEqualTo("STARBUCKS #12345");
        assertThat(draft.cleanMerchant()).isEqualTo("STARBUCKS");
        assertThat(draft.category().name()).isEqualTo("Coffee Shop");
        assertThat(draft.date()).isEqualTo(SEP_5);
        assertThat(draft.amount()).isEqualByComparingTo("12.50");
        assertThat(draft.duplicate()).isFalse();
    }

    @Test
    @DisplayName("Should check saved transactions with the amount's absolute value")
    void shouldCheckSavedDuplicatesIgnoringSign() {
        when(deduplicationService.isDuplicate(userId, SEP_5, new BigDecimal("45000"))).thenReturn(true);

        DraftTransaction draft = intake.draft(userId, new RawEntry(SEP_5, "HIGHLANDS", new BigDecimal("-45000")));

        assertThat(draft.duplicate()).isTrue();
        assertThat(draft.amount()).isEqualByComparingTo("-45000");
    }

    @Test
    @DisplayName("Should flag an entry that repeats an earlier entry within the window, without asking the database")
    void shouldFlagRepeatsWithinOneUpload() {
        List<DraftTransaction> drafts = intake.draft(userId, List.of(
            new RawEntry(SEP_5, "GRAB", new BigDecimal("35000")),
            new RawEntry(SEP_5.plusDays(2), "GRAB", new BigDecimal("-35000")),
            new RawEntry(SEP_5.plusDays(10), "GRAB", new BigDecimal("35000")),
            new RawEntry(SEP_5, "GRAB", new BigDecimal("36000"))));

        assertThat(drafts).extracting(DraftTransaction::duplicate).containsExactly(false, true, false, false);
        verify(deduplicationService, times(3)).isDuplicate(any(), any(), any());
    }

    @Test
    @DisplayName("Should never flag entries that are missing a date or an amount")
    void shouldNotFlagIncompleteEntries() {
        List<DraftTransaction> drafts = intake.draft(userId, List.of(
            new RawEntry(null, "GRAB", new BigDecimal("35000")),
            new RawEntry(SEP_5, "GRAB", null),
            new RawEntry(null, null, null)));

        assertThat(drafts).extracting(DraftTransaction::duplicate).containsExactly(false, false, false);
        verify(deduplicationService, never()).isDuplicate(any(), any(), any());
    }
}
