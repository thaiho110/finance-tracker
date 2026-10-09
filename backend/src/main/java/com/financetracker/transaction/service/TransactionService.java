package com.financetracker.transaction.service;

import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.model.SourceType;
import com.financetracker.transaction.model.Transaction;
import com.financetracker.transaction.repository.ReceiptItemRepository;
import com.financetracker.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.financetracker.transaction.dto.TransactionSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final Counter transactionsCreated;

    @Transactional
    public List<TransactionResponse> batchCreate(List<TransactionRequest> requests, UUID userId, String clientId) {
        List<Transaction> transactions = requests.stream()
            .map(req -> Transaction.builder()
                .date(req.date())
                .rawDescription(req.rawDescription())
                .cleanMerchant(req.cleanMerchant())
                .amount(req.amount())
                .category(req.category())
                .sourceType(SourceType.valueOf(req.sourceType()))
                .clientId(clientId)
                .isDuplicate(false)
                .createdBy(userId)
                .build())
            .toList();

        List<Transaction> saved = transactionRepository.saveAll(transactions);
        transactionsCreated.increment(saved.size());

        return saved.stream().map(this::toResponse).toList();
    }

    public Page<TransactionResponse> findAll(UUID userId, String category, String dateFrom, String dateTo, Pageable pageable) {
        LocalDate from = dateFrom != null ? LocalDate.parse(dateFrom) : null;
        LocalDate to = dateTo != null ? LocalDate.parse(dateTo) : null;

        Page<Transaction> page;
        if (category != null) {
            page = transactionRepository.findByCreatedByAndCategory(userId, category, pageable);
        } else {
            page = transactionRepository.findByFilters(userId, null, from, to, pageable);
        }
        return page.map(this::toResponse);
    }

    public TransactionResponse findById(UUID id, UUID userId) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));

        if (!transaction.getCreatedBy().equals(userId)) {
            throw new com.financetracker.common.exception.AuthenticationException("Access denied");
        }

        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request, UUID userId) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));

        if (!transaction.getCreatedBy().equals(userId)) {
            throw new com.financetracker.common.exception.AuthenticationException("Access denied");
        }

        transaction.setDate(request.date());
        transaction.setRawDescription(request.rawDescription());
        transaction.setCleanMerchant(request.cleanMerchant());
        transaction.setAmount(request.amount());
        transaction.setCategory(request.category());
        transaction.setSourceType(SourceType.valueOf(request.sourceType()));

        Transaction saved = transactionRepository.save(transaction);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));

        if (!transaction.getCreatedBy().equals(userId)) {
            throw new com.financetracker.common.exception.AuthenticationException("Access denied");
        }

        receiptItemRepository.deleteByTransactionId(id);
        transactionRepository.delete(transaction);
    }

    public List<String> getCategories(UUID userId) {
        return transactionRepository.findDistinctCategoriesByCreatedBy(userId);
    }

    public TransactionSummaryResponse getSummary(UUID userId) {
        // Category breakdown
        List<Object[]> categoryRows = transactionRepository.categoryBreakdownNative(userId);
        List<TransactionSummaryResponse.CategoryBreakdown> categoryBreakdown = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (Object[] row : categoryRows) {
            String category = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            categoryBreakdown.add(new TransactionSummaryResponse.CategoryBreakdown(category, total));
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                totalExpenses = totalExpenses.add(total);
            } else {
                totalIncome = totalIncome.add(total);
            }
        }

        // Monthly trend
        List<Object[]> monthlyRows = transactionRepository.monthlyTrendNative(userId);
        List<TransactionSummaryResponse.MonthlyTrend> monthlyTrend = new ArrayList<>();
        for (Object[] row : monthlyRows) {
            String month = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            monthlyTrend.add(new TransactionSummaryResponse.MonthlyTrend(month, total));
        }

        // Recent transactions
        List<Transaction> recent = transactionRepository.findRecentByCreatedBy(userId,
            org.springframework.data.domain.PageRequest.of(0, 5));
        List<TransactionResponse> recentTransactions = recent.stream().map(this::toResponse).toList();

        // Count and average
        long count = transactionRepository.countByCreatedBy(userId);
        BigDecimal totalAll = totalExpenses.add(totalIncome);
        BigDecimal average = count > 0
            ? totalAll.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new TransactionSummaryResponse(
            totalExpenses.abs(),
            totalIncome,
            count,
            average,
            Collections.unmodifiableList(categoryBreakdown),
            Collections.unmodifiableList(monthlyTrend),
            Collections.unmodifiableList(recentTransactions)
        );
    }

    private TransactionResponse toResponse(Transaction t) {
        List<TransactionResponse.ReceiptItemResponse> items = receiptItemRepository
            .findByTransactionId(t.getId())
            .stream()
            .map(item -> new TransactionResponse.ReceiptItemResponse(item.getId(), item.getItemDescription(), item.getPrice()))
            .toList();

        return new TransactionResponse(
            t.getId(), t.getDate(), t.getRawDescription(), t.getCleanMerchant(),
            t.getAmount(), t.getCategory(), t.getSourceType().name(),
            t.getClientId(), t.isDuplicate(),
            t.getCreatedAt() != null ? t.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toLocalDateTime() : null,
            items);
    }
}
