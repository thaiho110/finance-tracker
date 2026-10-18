package com.financetracker.csv.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.common.dto.ParsedTransactionResponse;
import com.financetracker.transaction.intake.TransactionIntake;
import com.financetracker.transaction.service.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsvImportService Unit Tests")
class CsvImportServiceTest {

    @Mock private CategorizationService categorizationService;
    @Mock private DeduplicationService deduplicationService;

    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService = new CsvImportService(new TransactionIntake(categorizationService, deduplicationService));
        lenient().when(categorizationService.cleanMerchant(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(categorizationService.categorize(anyString())).thenReturn("Other");
        lenient().when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);
    }

    private List<ParsedTransactionResponse> parse(String csv) {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return csvImportService.parse(file, UUID.randomUUID());
    }

    @Test
    @DisplayName("Should parse standard CSV with default headers")
    void shouldParseStandardCsv() {
        when(categorizationService.cleanMerchant("STARBUCKS #12345")).thenReturn("STARBUCKS");
        when(categorizationService.categorize("STARBUCKS #12345")).thenReturn("Coffee Shop");

        List<ParsedTransactionResponse> results = parse("""
            date,description,amount
            2024-01-15,STARBUCKS #12345,12.50
            2024-01-16,AMAZON PURCHASE,99.99
            """);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rowIndex()).isZero();
        assertThat(results.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(results.get(0).cleanMerchant()).isEqualTo("STARBUCKS");
        assertThat(results.get(0).amount()).isEqualByComparingTo("12.50");
        assertThat(results.get(0).category()).isEqualTo("Coffee Shop");
        assertThat(results.get(1).rowIndex()).isEqualTo(1);
        assertThat(results.get(1).amount()).isEqualByComparingTo("99.99");
    }

    @Test
    @DisplayName("Should parse CSV with alternative headers and month-first dates when day-first is impossible")
    void shouldParseAlternativeHeaders() {
        List<ParsedTransactionResponse> results = parse("""
            Transaction Date,Payee,Total
            01/15/2024,"STARBUCKS #12345",12.50
            """);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(results.get(0).rawDescription()).isEqualTo("STARBUCKS #12345");
        assertThat(results.get(0).amount()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("Should read Vietnamese statements: day-first dates and dot-grouped VND amounts")
    void shouldParseVietnameseStatement() {
        List<ParsedTransactionResponse> results = parse("""
            ﻿Ngày giao dịch,Nội dung,Số tiền
            05/09/2026,HIGHLANDS COFFEE,"-45.000"
            06/09/2026,BACH HOA XANH,"1.240.000"
            """);

        assertThat(results).extracting(ParsedTransactionResponse::date)
            .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6));
        assertThat(results.get(0).amount()).isEqualByComparingTo("-45000");
        assertThat(results.get(1).amount()).isEqualByComparingTo("1240000");
    }

    @Test
    @DisplayName("Should return empty list when CSV file has no data rows")
    void shouldReturnEmptyWhenNoDataRows() {
        assertThat(parse("date,description,amount\n")).isEmpty();
    }

    @Test
    @DisplayName("Should leave unreadable cells empty instead of defaulting to today and zero")
    void shouldLeaveUnreadableCellsEmpty() {
        List<ParsedTransactionResponse> results = parse("""
            date,description
            not a date,STARBUCKS
            """);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).date()).isNull();
        assertThat(results.get(0).amount()).isNull();
        assertThat(results.get(0).isDuplicate()).isFalse();
    }

    @Test
    @DisplayName("Should flag a row that repeats an earlier row in the same file")
    void shouldFlagRepeatedRowsInOneFile() {
        List<ParsedTransactionResponse> results = parse("""
            date,description,amount
            05/09/2026,GRAB,"35.000"
            06/09/2026,GRAB,"35.000"
            20/09/2026,GRAB,"35.000"
            """);

        assertThat(results).extracting(ParsedTransactionResponse::isDuplicate).containsExactly(false, true, false);
    }

    @Test
    @DisplayName("Should flag rows that match a saved transaction")
    void shouldFlagSavedDuplicates() {
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(true);

        List<ParsedTransactionResponse> results = parse("""
            date,description,amount
            2024-01-15,STARBUCKS,12.50
            """);

        assertThat(results.get(0).isDuplicate()).isTrue();
        assertThat(results.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(12.50));
    }
}
