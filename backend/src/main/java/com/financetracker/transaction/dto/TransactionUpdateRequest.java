package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The fields a user can correct on a saved transaction. Where it came from (source type) and its
 * receipt line items are fixed at import, so they aren't part of an update.
 */
@Schema(description = "Corrections to a saved transaction")
public record TransactionUpdateRequest(
    @Schema(example = "2026-10-17")
    @NotNull LocalDate date,

    @Schema(description = "Original description; blank keeps the merchant name", example = "Bach Hoa Xanh", nullable = true)
    String rawDescription,

    @Schema(example = "Bach Hoa Xanh")
    @NotBlank String cleanMerchant,

    @Schema(description = "Amount, positive", example = "90000")
    @NotNull @Positive BigDecimal amount,

    @Schema(example = "Groceries")
    @NotBlank String category
) { }
