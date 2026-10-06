package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Complete transaction record")
public record TransactionResponse(
    @Schema(description = "Transaction unique identifier") UUID id,
    @Schema(description = "Transaction date", example = "2024-01-15") LocalDate date,
    @Schema(description = "Raw description from source", example = "STARBUCKS #12345") String rawDescription,
    @Schema(description = "Cleaned merchant name", example = "STARBUCKS") String cleanMerchant,
    @Schema(description = "Transaction amount", example = "12.50") BigDecimal amount,
    @Schema(description = "Category", example = "Coffee Shop") String category,
    @Schema(description = "Source type", example = "CSV") String sourceType,
    @Schema(description = "Client identifier", example = "finance-tracker-web") String clientId,
    @Schema(description = "Whether this was flagged as duplicate", example = "false") boolean isDuplicate,
    @Schema(description = "Record creation timestamp") LocalDateTime createdAt,
    @Schema(description = "Receipt line items (only for OCR-sourced transactions)") List<ReceiptItemResponse> receiptItems
) {
    public record ReceiptItemResponse(UUID id, String itemDescription, BigDecimal price) {}
}
