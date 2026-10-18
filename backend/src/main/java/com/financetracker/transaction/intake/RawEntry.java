package com.financetracker.transaction.intake;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction as an import source read it (a CSV row, a scanned receipt), before it is
 * categorized or checked for duplicates. A field is null when the source could not read it.
 *
 * @param date        transaction date
 * @param description merchant or description exactly as written on the source
 * @param amount      amount as written; may be negative for debits
 */
public record RawEntry(LocalDate date, String description, BigDecimal amount) { }
