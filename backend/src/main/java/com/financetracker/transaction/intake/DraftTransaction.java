package com.financetracker.transaction.intake;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A {@link RawEntry} ready for the user to review: categorized and checked for duplicates, not yet saved.
 *
 * @param date           transaction date, or null when the source had none
 * @param rawDescription description as written on the source
 * @param cleanMerchant  display form of the merchant (see {@code CategorizationService.cleanMerchant})
 * @param amount         amount as written, or null when the source had none
 * @param category       detected category; "Other" when nothing matched
 * @param duplicate      true when a saved transaction or an earlier entry in the same upload has the
 *                       same amount within the duplicate window
 */
public record DraftTransaction(
    LocalDate date,
    String rawDescription,
    String cleanMerchant,
    BigDecimal amount,
    String category,
    boolean duplicate
) { }
