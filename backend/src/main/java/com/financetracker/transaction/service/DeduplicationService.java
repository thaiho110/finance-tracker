package com.financetracker.transaction.service;

import com.financetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final TransactionRepository transactionRepository;

    /**
     * Checks if a transaction is a potential duplicate within a 2-day window.
     * A duplicate is defined as: same user, same date (±2 days), same amount.
     */
    public boolean isDuplicate(UUID userId, LocalDate date, BigDecimal amount) {
        Instant since = date.atStartOfDay(java.time.ZoneOffset.UTC)
            .minusDays(2)
            .toInstant();
        return transactionRepository.existsDuplicate(userId, date, amount, since);
    }
}
