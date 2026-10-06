package com.financetracker.csv.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.common.dto.ParsedTransactionResponse;
import com.financetracker.transaction.service.DeduplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsvImportService Unit Tests")
class CsvImportServiceTest {

    @Mock private CategorizationService categorizationService;
    @Mock private DeduplicationService deduplicationService;

    @InjectMocks private CsvImportService csvImportService;

    @Test
    @DisplayName("Should parse standard CSV with default headers")
    void shouldParseStandardCsv() {
        String csv = """
            date,description,amount
            2024-01-15,STARBUCKS #12345,12.50
            2024-01-16,AMAZON PURCHASE,99.99
            """;

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
            "text/csv", csv.getBytes());

        when(categorizationService.cleanMerchant("STARBUCKS #12345")).thenReturn("STARBUCKS");
        when(categorizationService.categorize("STARBUCKS")).thenReturn("Coffee Shop");
        when(categorizationService.cleanMerchant("AMAZON PURCHASE")).thenReturn("AMAZON");
        when(categorizationService.categorize("AMAZON")).thenReturn("Online Shopping");
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);

        List<ParsedTransactionResponse> results = csvImportService.parse(file, UUID.randomUUID());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).cleanMerchant()).isEqualTo("STARBUCKS");
        assertThat(results.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(12.50));
        assertThat(results.get(0).category()).isEqualTo("Coffee Shop");

        assertThat(results.get(1).cleanMerchant()).isEqualTo("AMAZON");
        assertThat(results.get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        assertThat(results.get(1).category()).isEqualTo("Online Shopping");
    }

    @Test
    @DisplayName("Should parse CSV with alternative headers")
    void shouldParseAlternativeHeaders() {
        String csv = """
            Transaction Date,Payee,Total
            01/15/2024,"STARBUCKS #12345",12.50
            """;

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
            "text/csv", csv.getBytes());

        when(categorizationService.cleanMerchant("STARBUCKS #12345")).thenReturn("STARBUCKS");
        when(categorizationService.categorize("STARBUCKS")).thenReturn("Coffee Shop");
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);

        List<ParsedTransactionResponse> results = csvImportService.parse(file, UUID.randomUUID());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).cleanMerchant()).isEqualTo("STARBUCKS");
        assertThat(results.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(12.50));
    }

    @Test
    @DisplayName("Should return empty list when CSV file has no data rows")
    void shouldReturnEmptyWhenNoDataRows() {
        String csv = "date,description,amount\n";
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv",
            "text/csv", csv.getBytes());

        List<ParsedTransactionResponse> results = csvImportService.parse(file, UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle CSV with missing amount column")
    void shouldHandleMissingAmount() {
        String csv = """
            date,description
            2024-01-15,STARBUCKS
            """;

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
            "text/csv", csv.getBytes());

        when(categorizationService.cleanMerchant("STARBUCKS")).thenReturn("STARBUCKS");
        when(categorizationService.categorize("STARBUCKS")).thenReturn("Coffee Shop");
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);

        List<ParsedTransactionResponse> results = csvImportService.parse(file, UUID.randomUUID());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
