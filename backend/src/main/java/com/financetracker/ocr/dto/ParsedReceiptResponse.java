package com.financetracker.ocr.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Result of receipt OCR extraction. Fields that could not be read are null; see warnings.")
public record ParsedReceiptResponse(
    @Schema(description = "Extracted merchant name, or null if not found", example = "Bach Hoa Xanh", nullable = true)
    String merchant,
    @Schema(description = "Extracted receipt date, or null if missing or implausible", example = "2026-10-17", nullable = true)
    LocalDate date,
    @Schema(description = "Total amount on the receipt in VND, or null if not found", example = "184500", nullable = true)
    BigDecimal totalAmount,
    @Schema(description = "Auto-detected category", example = "Food & Dining")
    String category,
    @Schema(description = "Whether a transaction with the same date (±2 days) and amount already exists", example = "false")
    boolean isDuplicate,
    @Schema(description = "Individual line items from the receipt (best effort, may be empty)")
    List<LineItem> lineItems,
    @Schema(description = "What to double-check: TOTAL_NOT_FOUND, TOTAL_GUESSED, DATE_NOT_FOUND, DATE_SUSPICIOUS, "
        + "MERCHANT_NOT_FOUND, ITEMS_DO_NOT_MATCH_TOTAL")
    List<String> warnings
) {
    public ParsedReceiptResponse {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record LineItem(
        @Schema(description = "Item description as printed", example = "RAU MUONG") String item,
        @Schema(description = "Line price in VND", example = "12000") BigDecimal price
    ) { }
}
