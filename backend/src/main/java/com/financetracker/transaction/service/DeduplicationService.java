package com.financetracker.transaction.service;

import com.financetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    /** Saved transactions this many days before or after a date, with the same amount, count as duplicates. */
    public static final int WINDOW_DAYS = 2;

    private final TransactionRepository transactionRepository;

    /**
     * True when the user already saved a transaction with this amount dated within
     * {@value #WINDOW_DAYS} days of {@code date}. The window is on the transaction date, not on when
     * it was saved. Missing date or amount is never a duplicate.
     */
    public boolean isDuplicate(UUID userId, LocalDate date, BigDecimal amount) {
        return date != null && amount != null
            && transactionRepository.existsDuplicate(userId, date.minusDays(WINDOW_DAYS), date.plusDays(WINDOW_DAYS), amount);
    }
}
