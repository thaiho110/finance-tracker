package com.financetracker.transaction.intake;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.transaction.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns what an import source read into drafts the user reviews before saving. Every source
 * (CSV, receipt OCR, later manual entry) goes through here, so categorization and duplicate
 * rules are the same whichever way a transaction arrives.
 */
@Service
@RequiredArgsConstructor
public class TransactionIntake {

    private final CategorizationService categorizationService;
    private final DeduplicationService deduplicationService;

    /**
     * Drafts the entries of one upload, in the same order.
     *
     * <p>An entry is a possible duplicate when the user already saved a transaction with the same
     * amount (ignoring sign) within {@value DeduplicationService#WINDOW_DAYS} days of its date, or an
     * earlier entry in this upload matches the same way. Entries missing a date or amount are never
     * flagged.
     */
    public List<DraftTransaction> draft(UUID userId, List<RawEntry> entries) {
        List<DraftTransaction> drafts = new ArrayList<>(entries.size());
        for (RawEntry entry : entries) {
            boolean duplicate = isComplete(entry)
                && (repeatsEarlierEntry(entry, drafts)
                    || deduplicationService.isDuplicate(userId, entry.date(), entry.amount().abs()));
            drafts.add(new DraftTransaction(
                entry.date(),
                entry.description(),
                categorizationService.cleanMerchant(entry.description()),
                entry.amount(),
                categorizationService.categorize(entry.description()),
                duplicate));
        }
        return drafts;
    }

    /** Drafts a single entry, e.g. one scanned receipt. */
    public DraftTransaction draft(UUID userId, RawEntry entry) {
        return draft(userId, List.of(entry)).get(0);
    }

    private static boolean isComplete(RawEntry entry) {
        return entry.date() != null && entry.amount() != null;
    }

    private static boolean repeatsEarlierEntry(RawEntry entry, List<DraftTransaction> earlier) {
        return earlier.stream().anyMatch(previous -> previous.date() != null && previous.amount() != null
            && previous.amount().abs().compareTo(entry.amount().abs()) == 0
            && Math.abs(ChronoUnit.DAYS.between(previous.date(), entry.date())) <= DeduplicationService.WINDOW_DAYS);
    }
}
