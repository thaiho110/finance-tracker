package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Transaction data")
public record TransactionRequest(
    @Schema(description = "Transaction date", example = "2024-01-15")
    @NotNull LocalDate date,

    @Schema(description = "Raw description from bank statement or receipt", example = "STARBUCKS #12345")
    @NotBlank String rawDescription,

    @Schema(description = "Cleaned merchant name", example = "STARBUCKS")
    @NotBlank String cleanMerchant,

    @Schema(description = "Transaction amount (positive value)", example = "12.50")
    @Positive BigDecimal amount,

    @Schema(description = "Transaction category", example = "Coffee Shop")
    @NotBlank String category,

    @Schema(description = "Source type", example = "CSV")
    @NotBlank String sourceType,

    @Schema(description = "Receipt line items; saved only for OCR transactions", nullable = true)
    @Valid List<ReceiptLineItemRequest> lineItems
) {
    /** Copies the line items so the request can't be changed through the caller's list. */
    public TransactionRequest {
        lineItems = lineItems == null ? null : List.copyOf(lineItems);
    }
}
