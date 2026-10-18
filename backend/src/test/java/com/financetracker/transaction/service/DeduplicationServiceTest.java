package com.financetracker.transaction.service;

import com.financetracker.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeduplicationService Unit Tests")
class DeduplicationServiceTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private DeduplicationService deduplicationService;

    @Test
    @DisplayName("Should detect duplicate transaction")
    void shouldDetectDuplicate() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 15);
        BigDecimal amount = BigDecimal.valueOf(12.50);

        when(transactionRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(true);

        boolean result = deduplicationService.isDuplicate(userId, date, amount);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not flag new transaction as duplicate")
    void shouldNotFlagNewTransaction() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 15);
        BigDecimal amount = BigDecimal.valueOf(12.50);

        when(transactionRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(false);

        boolean result = deduplicationService.isDuplicate(userId, date, amount);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should look two days either side of the transaction date")
    void shouldUseTransactionDateWindow() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 5);
        BigDecimal amount = BigDecimal.valueOf(45_000);

        deduplicationService.isDuplicate(userId, date, amount);

        verify(transactionRepository).existsDuplicate(userId, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7), amount);
    }

    @Test
    @DisplayName("Should not query when the date or amount is missing")
    void shouldSkipIncompleteValues() {
        assertThat(deduplicationService.isDuplicate(UUID.randomUUID(), null, BigDecimal.TEN)).isFalse();
        assertThat(deduplicationService.isDuplicate(UUID.randomUUID(), LocalDate.of(2026, 9, 5), null)).isFalse();
        verifyNoInteractions(transactionRepository);
    }
}
