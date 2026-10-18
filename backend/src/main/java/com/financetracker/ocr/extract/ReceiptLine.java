package com.financetracker.ocr.extract;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One line of OCR text with the views the parser needs: the original text, an accent-free
 * uppercase copy for keyword matching, and the VND amounts found on it.
 */
final class ReceiptLine {

    /**
     * A money amount: thousands-separated ("184.500", "1,240,000") or plain digits that end in "00"
     * ("65000"). Plain numbers must end in 00 because VND prices do, while store codes, invoice
     * numbers and years usually don't. A trailing ".00"/",00" is dropped.
     */
    private static final Pattern AMOUNT = Pattern.compile(
        "(?<![\\d.,])(\\d{1,3}(?:[.,]\\d{3})+|[1-9]\\d{1,7}00)(?:[.,]00)?(?![\\d]|[.,]\\d)");

    /** Dates and clock times are removed before looking for amounts, so "17/10/2026" isn't read as 2026. */
    private static final Pattern DATE_OR_TIME = Pattern.compile(
        "\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}|\\d{1,2}:\\d{2}(?::\\d{2})?");

    /** OCR often reads 0 as O inside numbers ("12.OOO"); this finds number-like runs to repair. */
    private static final Pattern NUMBERISH = Pattern.compile("(?<![A-Za-z])[\\dOo][\\dOo.,]*[\\dOo](?![A-Za-z])");

    /** Currency marks that may follow an amount at the end of a line. */
    private static final Pattern TRAILING_CURRENCY = Pattern.compile("\\s*(VND|VNĐ|ĐỒNG|DONG|Đ|D|₫)\\.?\\s*$",
        Pattern.CASE_INSENSITIVE);

    private static final int MIN_LETTERS_FOR_TEXT = 3;

    private final String original;
    private final String normalized;
    private final List<Amount> amounts;

    ReceiptLine(String original) {
        this.original = original.strip();
        this.normalized = normalize(this.original);
        this.amounts = findAmounts(this.original);
    }

    /** An amount and where it ends in the original line. */
    record Amount(BigDecimal value, int end) { }

    String original() {
        return original;
    }

    /** Uppercase, accents removed, Đ→D, single spaces — for keyword matching. */
    String normalized() {
        return normalized;
    }

    List<Amount> amounts() {
        return Collections.unmodifiableList(amounts);
    }

    boolean hasAmount() {
        return !amounts.isEmpty();
    }

    BigDecimal lastAmount() {
        return amounts.isEmpty() ? null : amounts.get(amounts.size() - 1).value();
    }

    /** True when the line ends with an amount (optionally followed by a currency mark). */
    boolean endsWithAmount() {
        if (amounts.isEmpty()) {
            return false;
        }
        String rest = original.substring(amounts.get(amounts.size() - 1).end());
        return TRAILING_CURRENCY.matcher(rest).replaceAll("").isBlank();
    }

    int letterCount() {
        int count = 0;
        for (int i = 0; i < original.length(); i++) {
            if (Character.isLetter(original.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    boolean hasText() {
        return letterCount() >= MIN_LETTERS_FOR_TEXT;
    }

    boolean containsAny(List<String> phrases) {
        for (String phrase : phrases) {
            if (containsPhrase(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** Whole-word match of an already-normalized phrase. */
    boolean containsPhrase(String phrase) {
        String padded = " " + normalized.replaceAll("[^A-Z0-9]+", " ") + " ";
        return padded.contains(" " + phrase + " ");
    }

    static String normalize(String text) {
        String withoutDStroke = text.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(withoutDStroke, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .strip();
    }

    private static List<Amount> findAmounts(String text) {
        String cleaned = repairDigits(DATE_OR_TIME.matcher(text).replaceAll(m -> " ".repeat(m.group().length())));
        List<Amount> found = new ArrayList<>();
        Matcher matcher = AMOUNT.matcher(cleaned);
        while (matcher.find()) {
            String digits = matcher.group(1).replaceAll("[.,]", "");
            found.add(new Amount(new BigDecimal(digits), matcher.end()));
        }
        return found;
    }

    private static String repairDigits(String text) {
        Matcher matcher = NUMBERISH.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String run = matcher.group();
            String replacement = run.chars().anyMatch(Character::isDigit) ? run.replace('O', '0').replace('o', '0') : run;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
