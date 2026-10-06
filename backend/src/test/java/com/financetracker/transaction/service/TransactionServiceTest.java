package com.financetracker.transaction.service;

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
    @DisplayName("Should find transaction by ID")
    void shouldFindById() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        TransactionResponse response = transactionService.findById(transactionId, userId);

        assertThat(response.id()).isEqualTo(transactionId);
        assertThat(response.cleanMerchant()).isEqualTo("STARBUCKS");
    }

    @Test
    @DisplayName("Should throw when transaction not found")
    void shouldThrowWhenNotFound() {
        when(transactionRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findById(UUID.randomUUID(), userId))
            .isInstanceOf(com.financetracker.common.exception.EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Should update transaction")
    void shouldUpdate() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));
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
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        transactionService.delete(transactionId, userId);

        verify(receiptItemRepository).deleteByTransactionId(transactionId);
        verify(transactionRepository).delete(testTransaction);
    }
}
