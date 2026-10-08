package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Transaction data")
public record TransactionRequest(
    @Schema(description = "Transaction date", example = "2024-01-15")
    @NotBlank LocalDate date,

    @Schema(description = "Raw description from bank statement or receipt", example = "STARBUCKS #12345")
    @NotBlank String rawDescription,

    @Schema(description = "Cleaned merchant name", example = "STARBUCKS")
    @NotBlank String cleanMerchant,

    @Schema(description = "Transaction amount (positive value)", example = "12.50")
    @Positive BigDecimal amount,

    @Schema(description = "Transaction category", example = "Coffee Shop")
    @NotBlank String category,

    @Schema(description = "Source type", example = "CSV")
    @NotBlank String sourceType
) { }
