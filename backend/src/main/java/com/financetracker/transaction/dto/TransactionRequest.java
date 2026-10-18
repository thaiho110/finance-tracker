package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Transaction data")
public record TransactionRequest(
    @Schema(description = "Transaction date", example = "2024-01-15")
    @NotNull LocalDate date,

    @Schema(description = "Raw description from bank statement or receipt", example = "STARBUCKS #12345")
    @NotBlank String rawDescription,

    @Schema(description = "Cleaned merchant name", example = "STARBUCKS")
    @NotBlank String cleanMerchant,

    @Schema(description = "Amount; how it counts comes from the category's kind. Negative means a refund", example = "45000")
    @NotNull BigDecimal amount,

    @Schema(description = "Category id; takes precedence over category", nullable = true)
    UUID categoryId,

    @Schema(description = "Category name, for older clients; an unknown name creates one of the user's categories",
        example = "Coffee Shop", nullable = true)
    String category,

    @Schema(description = "Source type", example = "CSV")
    @NotBlank String sourceType,

    @Schema(description = "Receipt line items; saved only for OCR transactions", nullable = true)
    @Valid List<ReceiptLineItemRequest> lineItems
) {
    /** Copies the line items so the request can't be changed through the caller's list. */
    public TransactionRequest {
        lineItems = lineItems == null ? null : List.copyOf(lineItems);
    }

    @AssertTrue(message = "must not be zero")
    @Schema(hidden = true)
    public boolean isAmountNonZero() {
        return amount == null || amount.signum() != 0;
    }
}
