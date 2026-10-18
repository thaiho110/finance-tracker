package com.financetracker.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "A single parsed CSV transaction row before saving")
public record ParsedTransactionResponse(
    @Schema(description = "Row index in the CSV file", example = "0") int rowIndex,
    @Schema(description = "Parsed transaction date", example = "2024-01-15") LocalDate date,
    @Schema(description = "Raw description from CSV", example = "STARBUCKS #12345") String rawDescription,
    @Schema(description = "Cleaned merchant name", example = "STARBUCKS") String cleanMerchant,
    @Schema(description = "Parsed amount", example = "12.50") BigDecimal amount,
    @Schema(description = "Auto-detected category id") UUID categoryId,
    @Schema(description = "Auto-detected category name", example = "Coffee Shop") String category,
    @Schema(description = "Whether this transaction appears to be a duplicate", example = "false") boolean isDuplicate
) { }
