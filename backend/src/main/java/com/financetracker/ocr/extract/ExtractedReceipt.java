package com.financetracker.ocr.extract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fields read from a receipt's OCR text by {@link ReceiptTextParser}.
 * Any field the parser could not find is {@code null}; {@link #warnings()} explains why.
 *
 * @param merchant       merchant name, or null when not found
 * @param date           receipt date, or null when missing or implausible
 * @param total          total amount in VND, or null when not found
 * @param lineItems      line items in receipt order (best effort, may be empty)
 * @param warnings       codes from {@link ReceiptWarnings}
 * @param amountsFound   whether any money amount appeared anywhere in the text
 */
public record ExtractedReceipt(
    String merchant,
    LocalDate date,
    BigDecimal total,
    List<LineItem> lineItems,
    List<String> warnings,
    boolean amountsFound
) {
    public ExtractedReceipt {
        lineItems = List.copyOf(lineItems);
        warnings = List.copyOf(warnings);
    }

    /**
     * One receipt line: item description and the line's price (already multiplied by quantity).
     *
     * @param item  item description as printed
     * @param price line price in VND
     */
    public record LineItem(String item, BigDecimal price) { }
}
