package com.financetracker.transaction.service;

import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.service.CategoryService;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.dto.TransactionSummaryResponse;
import com.financetracker.transaction.dto.TransactionUpdateRequest;
import com.financetracker.transaction.model.ReceiptItem;
import com.financetracker.transaction.model.SourceType;
import com.financetracker.transaction.model.Transaction;
import com.financetracker.transaction.repository.ReceiptItemRepository;
import com.financetracker.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int RECENT_COUNT = 5;
    /** Columns of {@code categoryBreakdownNative}: id, name, kind, color, total. */
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_KIND = 2;
    private static final int COL_COLOR = 3;
    private static final int COL_TOTAL = 4;

    private final TransactionRepository transactionRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final CategoryService categoryService;
    private final Counter transactionsCreated;

    @Transactional
    public List<TransactionResponse> batchCreate(List<TransactionRequest> requests, UUID userId, String clientId) {
        List<Transaction> transactions = requests.stream()
            .map(req -> Transaction.builder()
                .date(req.date())
                .rawDescription(req.rawDescription())
                .cleanMerchant(req.cleanMerchant())
                .amount(req.amount())
                .categoryId(categoryService.resolveForTransaction(userId, req.categoryId(), req.category()).id())
                .sourceType(SourceType.valueOf(req.sourceType()))
                .clientId(clientId)
                .isDuplicate(false)
                .createdBy(userId)
                .build())
            .toList();

        List<Transaction> saved = transactionRepository.saveAll(transactions);
        transactionsCreated.increment(saved.size());
        saveReceiptItems(requests, saved);

        return toResponses(saved);
    }

    /** Stores receipt line items for OCR transactions; {@code saved} is in the same order as {@code requests}. */
    private void saveReceiptItems(List<TransactionRequest> requests, List<Transaction> saved) {
        List<ReceiptItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(requests.size(), saved.size()); i++) {
            TransactionRequest request = requests.get(i);
            if (request.lineItems() == null || !SourceType.OCR.name().equals(request.sourceType())) {
                continue;
            }
            UUID transactionId = saved.get(i).getId();
            request.lineItems().forEach(line -> items.add(ReceiptItem.builder()
                .transactionId(transactionId)
                .itemDescription(line.item().strip())
                .price(line.price())
                .build()));
        }
        if (!items.isEmpty()) {
            receiptItemRepository.saveAll(items);
        }
    }

    /** The user's transactions; every filter is optional and they combine (category and date range together). */
    public Page<TransactionResponse> findAll(UUID userId, UUID categoryId, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findByFilters(userId, categoryId, dateFrom, dateTo, pageable);
        Map<UUID, CategoryRef> refs = categoryService.refs(categoryIds(page.getContent()));
        return page.map(t -> toResponse(t, refs));
    }

    /** Category id for the legacy {@code ?category=<name>} filter: one of the user's visible categories, or none. */
    public UUID categoryIdByName(UUID userId, String name) {
        return categoryService.list(userId, true).stream()
            .filter(c -> c.name().equalsIgnoreCase(name.strip()))
            .map(c -> c.id())
            .findFirst()
            .orElse(null);
    }

    public TransactionResponse findById(UUID id, UUID userId) {
        return toResponses(List.of(owned(id, userId))).get(0);
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionUpdateRequest request, UUID userId) {
        Transaction transaction = owned(id, userId);
        String merchant = request.cleanMerchant().strip();
        String description = request.rawDescription() == null || request.rawDescription().isBlank()
            ? merchant
            : request.rawDescription().strip();
        transaction.setDate(request.date());
        transaction.setRawDescription(description);
        transaction.setCleanMerchant(merchant);
        transaction.setAmount(request.amount());
        transaction.setCategoryId(categoryService.resolveForTransaction(userId, request.categoryId(), request.category()).id());

        return toResponses(List.of(transactionRepository.save(transaction))).get(0);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Transaction transaction = owned(id, userId);
        receiptItemRepository.deleteByTransactionId(id);
        transactionRepository.delete(transaction);
    }

    /** How many transactions the user has saved (used by administration, which never sees the transactions). */
    public long countFor(UUID userId) {
        return transactionRepository.countByCreatedBy(userId);
    }

    /**
     * Totals for the dashboard. A transaction counts by its category's kind (ADR-015, amended): expense
     * categories add to spending, income categories to income, transfers to neither. Amounts are summed
     * as stored, so a refund (negative amount) in an expense category lowers spending.
     */
    public TransactionSummaryResponse getSummary(UUID userId) {
        List<TransactionSummaryResponse.CategoryBreakdown> categoryBreakdown = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (Object[] row : transactionRepository.categoryBreakdownNative(userId)) {
            CategoryKind kind = CategoryKind.valueOf((String) row[COL_KIND]);
            BigDecimal total = (BigDecimal) row[COL_TOTAL];
            categoryBreakdown.add(new TransactionSummaryResponse.CategoryBreakdown(
                (String) row[COL_NAME], total, (UUID) row[COL_ID], kind.name(), (String) row[COL_COLOR]));
            if (kind == CategoryKind.EXPENSE) {
                totalExpenses = totalExpenses.add(total);
            } else if (kind == CategoryKind.INCOME) {
                totalIncome = totalIncome.add(total);
            }
        }

        List<TransactionSummaryResponse.MonthlyTrend> monthlyTrend = transactionRepository.monthlyTrendNative(userId).stream()
            .map(row -> new TransactionSummaryResponse.MonthlyTrend((String) row[0], (BigDecimal) row[1]))
            .toList();

        List<TransactionResponse> recentTransactions = toResponses(
            transactionRepository.findRecentByCreatedBy(userId, PageRequest.of(0, RECENT_COUNT)));

        long count = transactionRepository.countByCreatedBy(userId);
        BigDecimal average = count > 0
            ? totalExpenses.add(totalIncome).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new TransactionSummaryResponse(totalExpenses, totalIncome, count, average,
            categoryBreakdown, monthlyTrend, recentTransactions);
    }

    /**
     * The transaction with this id if it belongs to the user. Another user's transaction is reported as
     * not found, so ids can't be probed.
     */
    private Transaction owned(UUID id, UUID userId) {
        return transactionRepository.findByIdAndCreatedBy(id, userId)
            .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));
    }

    private static Set<UUID> categoryIds(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getCategoryId).collect(Collectors.toSet());
    }

    private List<TransactionResponse> toResponses(List<Transaction> transactions) {
        Map<UUID, CategoryRef> refs = categoryService.refs(categoryIds(transactions));
        return transactions.stream().map(t -> toResponse(t, refs)).toList();
    }

    private TransactionResponse toResponse(Transaction t, Map<UUID, CategoryRef> refs) {
        List<TransactionResponse.ReceiptItemResponse> items = receiptItemRepository
            .findByTransactionId(t.getId())
            .stream()
            .map(item -> new TransactionResponse.ReceiptItemResponse(item.getId(), item.getItemDescription(), item.getPrice()))
            .toList();
        CategoryRef category = refs.get(t.getCategoryId());

        return new TransactionResponse(
            t.getId(), t.getDate(), t.getRawDescription(), t.getCleanMerchant(), t.getAmount(),
            category == null ? null : category.name(),
            t.getCategoryId(),
            category == null ? null : category.kind().name(),
            category == null ? null : category.color(),
            t.getSourceType().name(), t.getClientId(), t.isDuplicate(),
            t.getCreatedAt() != null ? t.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDateTime() : null,
            items);
    }
}
