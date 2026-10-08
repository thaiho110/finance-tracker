package com.financetracker.ocr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Result of receipt OCR extraction")
public record ParsedReceiptResponse(
    @Schema(description = "Extracted merchant name", example = "Starbucks Coffee") String merchant,
    @Schema(description = "Extracted receipt date", example = "2024-01-15") LocalDate date,
    @Schema(description = "Total amount on receipt", example = "12.50") BigDecimal totalAmount,
    @Schema(description = "Auto-detected category", example = "Coffee Shop") String category,
    @Schema(description = "Whether this receipt appears to be a duplicate", example = "false") boolean isDuplicate,
    @Schema(description = "Individual line items from the receipt") List<LineItem> lineItems
) {
    public record LineItem(String item, BigDecimal price) { }
}
