package com.financetracker.transaction.service;

import com.financetracker.transaction.dto.ReceiptLineItemRequest;
import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.model.ReceiptItem;
import com.financetracker.transaction.model.SourceType;
import com.financetracker.transaction.model.Transaction;
import com.financetracker.transaction.repository.ReceiptItemRepository;
import com.financetracker.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private ReceiptItemRepository receiptItemRepository;
    @Mock private Counter transactionsCreated;

    @InjectMocks private TransactionService transactionService;

    private UUID userId;
    private UUID transactionId;
    private Transaction testTransaction;
    private TransactionRequest testRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        testRequest = new TransactionRequest(
            LocalDate.of(2024, 1, 15),
            "STARBUCKS #12345",
            "STARBUCKS",
            BigDecimal.valueOf(12.50),
            "Coffee Shop",
            "CSV"
        );
        testTransaction = Transaction.builder()
            .id(transactionId)
            .date(LocalDate.of(2024, 1, 15))
            .rawDescription("STARBUCKS #12345")
            .cleanMerchant("STARBUCKS")
            .amount(BigDecimal.valueOf(12.50))
            .category("Coffee Shop")
            .sourceType(SourceType.CSV)
            .clientId("finance-tracker-web")
            .isDuplicate(false)
            .createdBy(userId)
            .build();
    }

    @Test
    @DisplayName("Should create transactions in batch")
    void shouldBatchCreate() {
        when(transactionRepository.saveAll(any())).thenReturn(List.of(testTransaction));

        List<TransactionResponse> results = transactionService.batchCreate(
            List.of(testRequest), userId, "finance-tracker-web");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).cleanMerchant()).isEqualTo("STARBUCKS");
        assertThat(results.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(12.50));
        verify(transactionsCreated).increment(1);
    }

    @Test
    @DisplayName("Should find transactions with pagination")
    void shouldFindAllPaginated() {
        Page<Transaction> page = new PageImpl<>(List.of(testTransaction));
        when(transactionRepository.findByFilters(any(), any(), any(), any(), any())).thenReturn(page);

        Page<TransactionResponse> result = transactionService.findAll(userId, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should combine category and date filters")
    void shouldCombineFilters() {
        Page<Transaction> page = new PageImpl<>(List.of(testTransaction));
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);
        PageRequest pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByFilters(userId, "Coffee Shop", from, to, pageable)).thenReturn(page);

        Page<TransactionResponse> result = transactionService.findAll(userId, "Coffee Shop", from, to, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should find transaction by ID")
    void shouldFindById() {
        when(transactionRepository.findByIdAndCreatedBy(transactionId, userId)).thenReturn(Optional.of(testTransaction));

        TransactionResponse response = transactionService.findById(transactionId, userId);

        assertThat(response.id()).isEqualTo(transactionId);
        assertThat(response.cleanMerchant()).isEqualTo("STARBUCKS");
    }

    @Test
    @DisplayName("Should throw when transaction not found")
    void shouldThrowWhenNotFound() {
        when(transactionRepository.findByIdAndCreatedBy(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findById(UUID.randomUUID(), userId))
            .isInstanceOf(com.financetracker.common.exception.EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Should report another user's transaction as not found and leave it untouched")
    void shouldHideOtherUsersTransactions() {
        UUID otherUser = UUID.randomUUID();
        when(transactionRepository.findByIdAndCreatedBy(transactionId, otherUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findById(transactionId, otherUser))
            .isInstanceOf(com.financetracker.common.exception.EntityNotFoundException.class);
        assertThatThrownBy(() -> transactionService.update(transactionId, testRequest, otherUser))
            .isInstanceOf(com.financetracker.common.exception.EntityNotFoundException.class);
        assertThatThrownBy(() -> transactionService.delete(transactionId, otherUser))
            .isInstanceOf(com.financetracker.common.exception.EntityNotFoundException.class);
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should update transaction")
    void shouldUpdate() {
        when(transactionRepository.findByIdAndCreatedBy(transactionId, userId)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any())).thenReturn(testTransaction);

        TransactionRequest updateRequest = new TransactionRequest(
            LocalDate.of(2024, 2, 1),
            "AMAZON PURCHASE",
            "AMAZON",
            BigDecimal.valueOf(99.99),
            "Online Shopping",
            "MANUAL"
        );

        TransactionResponse response = transactionService.update(transactionId, updateRequest, userId);

        assertThat(response.cleanMerchant()).isEqualTo("AMAZON");
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
    }

    @Test
    @DisplayName("Should delete transaction")
    void shouldDelete() {
        when(transactionRepository.findByIdAndCreatedBy(transactionId, userId)).thenReturn(Optional.of(testTransaction));

        transactionService.delete(transactionId, userId);

        verify(receiptItemRepository).deleteByTransactionId(transactionId);
        verify(transactionRepository).delete(testTransaction);
    }

    @Test
    @DisplayName("Should save receipt line items for OCR transactions")
    @SuppressWarnings("unchecked")
    void shouldSaveReceiptLineItemsForOcr() {
        TransactionRequest ocrRequest = new TransactionRequest(
            LocalDate.of(2026, 10, 17), "Bach Hoa Xanh", "Bach Hoa Xanh", BigDecimal.valueOf(90_000),
            "Food & Dining", "OCR",
            List.of(new ReceiptLineItemRequest("RAU MUONG ", BigDecimal.valueOf(12_000)),
                new ReceiptLineItemRequest("GAO ST25 2KG", BigDecimal.valueOf(78_000))));
        Transaction saved = Transaction.builder()
            .id(transactionId)
            .date(LocalDate.of(2026, 10, 17))
            .rawDescription("Bach Hoa Xanh")
            .cleanMerchant("Bach Hoa Xanh")
            .amount(BigDecimal.valueOf(90_000))
            .category("Food & Dining")
            .sourceType(SourceType.OCR)
            .clientId("finance-tracker-web")
            .isDuplicate(false)
            .createdBy(userId)
            .build();
        when(transactionRepository.saveAll(any())).thenReturn(List.of(saved));

        transactionService.batchCreate(List.of(ocrRequest), userId, "finance-tracker-web");

        ArgumentCaptor<List<ReceiptItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(receiptItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allSatisfy(item -> assertThat(item.getTransactionId()).isEqualTo(transactionId));
        assertThat(captor.getValue()).extracting(ReceiptItem::getItemDescription).containsExactly("RAU MUONG", "GAO ST25 2KG");
    }

    @Test
    @DisplayName("Should ignore line items on non-OCR transactions")
    void shouldIgnoreLineItemsForCsv() {
        TransactionRequest csvRequest = new TransactionRequest(
            LocalDate.of(2024, 1, 15), "STARBUCKS #12345", "STARBUCKS", BigDecimal.valueOf(12.50), "Coffee Shop", "CSV",
            List.of(new ReceiptLineItemRequest("Latte", BigDecimal.valueOf(12.50))));
        when(transactionRepository.saveAll(any())).thenReturn(List.of(testTransaction));

        transactionService.batchCreate(List.of(csvRequest), userId, "finance-tracker-web");

        verify(receiptItemRepository, never()).saveAll(any());
    }
}
