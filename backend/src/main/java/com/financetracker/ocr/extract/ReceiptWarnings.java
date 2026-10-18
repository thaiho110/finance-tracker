package com.financetracker.ocr.extract;

/**
 * Warning codes returned with a parsed receipt so the UI can tell the user what to double-check.
 */
public final class ReceiptWarnings {

    /** No line with a total keyword and no usable amount near the bottom of the receipt. */
    public static final String TOTAL_NOT_FOUND = "TOTAL_NOT_FOUND";
    /** No total keyword found; the largest amount near the bottom was used instead. */
    public static final String TOTAL_GUESSED = "TOTAL_GUESSED";
    /** No date found on the receipt. */
    public static final String DATE_NOT_FOUND = "DATE_NOT_FOUND";
    /** A date was found but is in the future or more than a year old, so it was discarded. */
    public static final String DATE_SUSPICIOUS = "DATE_SUSPICIOUS";
    /** No line looked like a merchant name. */
    public static final String MERCHANT_NOT_FOUND = "MERCHANT_NOT_FOUND";
    /** Line items were found but their sum (minus discounts) does not equal the total. */
    public static final String ITEMS_DO_NOT_MATCH_TOTAL = "ITEMS_DO_NOT_MATCH_TOTAL";

    private ReceiptWarnings() {
    }
}
