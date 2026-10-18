package com.financetracker.transaction.intake;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads single amount and date values (e.g. CSV cells) the way Vietnamese banks and shops write them:
 * a dot or comma before exactly three digits groups thousands ({@code 184.500} = 184500), and dates are
 * day-first ({@code 05/09/2026} = 5 September). Month-first dates are accepted only when day-first is
 * impossible ({@code 01/15/2024}).
 */
public final class LocalValues {

    private static final Pattern CURRENCY_AND_SPACES = Pattern.compile("(?iu)VNĐ|VND|đồng|dong|₫|đ|\\$|\\s|\\u00A0");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern YEAR_FIRST = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern DAY_FIRST = Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})");
    private static final Pattern DATE_TIME_SEPARATOR = Pattern.compile("[\\sT]");
    private static final int THOUSANDS_GROUP = 3;
    private static final int TWO_DIGIT_YEAR_BASE = 2000;
    private static final int TWO_DIGITS = 2;

    private LocalValues() {
    }

    /**
     * Parses an amount such as {@code 184.500}, {@code 1,240,000}, {@code 12.50}, {@code 1.240.000,50},
     * {@code -45.000 ₫} or {@code (45,000)}.
     *
     * @return the amount (negative for a leading/trailing minus or parentheses), or null when the text is not an amount
     */
    public static BigDecimal parseAmount(String text) {
        String value = text == null ? "" : CURRENCY_AND_SPACES.matcher(text).replaceAll("");
        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-") || value.startsWith("+")) {
            negative = value.charAt(0) == '-' || negative;
            value = value.substring(1);
        } else if (value.endsWith("-")) {
            negative = true;
            value = value.substring(0, value.length() - 1);
        }
        BigDecimal amount = null;
        if (NUMBER.matcher(value).matches()) {
            BigDecimal parsed = new BigDecimal(toPlainDecimal(value));
            amount = negative ? parsed.negate() : parsed;
        }
        return amount;
    }

    /**
     * Parses a date such as {@code 05/09/2026}, {@code 5-9-26}, {@code 2026-09-05} or {@code 05/09/2026 14:30}.
     *
     * @return the date, or null when the text is not a valid date
     */
    public static LocalDate parseDate(String text) {
        String head = text == null ? "" : DATE_TIME_SEPARATOR.split(text.strip(), 2)[0];
        LocalDate date = null;
        Matcher yearFirst = YEAR_FIRST.matcher(head);
        Matcher dayFirst = DAY_FIRST.matcher(head);
        if (yearFirst.matches()) {
            date = toDate(yearFirst.group(1), yearFirst.group(2), yearFirst.group(3));
        } else if (dayFirst.matches()) {
            String year = dayFirst.group(3).length() == TWO_DIGITS
                ? String.valueOf(TWO_DIGIT_YEAR_BASE + Integer.parseInt(dayFirst.group(3)))
                : dayFirst.group(3);
            date = toDate(year, dayFirst.group(2), dayFirst.group(1));
            if (date == null) {
                date = toDate(year, dayFirst.group(1), dayFirst.group(2));
            }
        }
        return date;
    }

    /** "184.500" → "184500", "12,50" → "12.50", "1,240,000.50" → "1240000.50". */
    private static String toPlainDecimal(String number) {
        int lastDot = number.lastIndexOf('.');
        int lastComma = number.lastIndexOf(',');
        String plain;
        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char group = decimal == '.' ? ',' : '.';
            plain = number.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char separator = lastDot >= 0 ? '.' : ',';
            long separators = number.chars().filter(c -> c == separator).count();
            int digitsAfterLast = number.length() - number.lastIndexOf(separator) - 1;
            boolean grouping = separators > 1 || digitsAfterLast == THOUSANDS_GROUP;
            plain = grouping ? number.replace(String.valueOf(separator), "") : number.replace(separator, '.');
        } else {
            plain = number;
        }
        return plain;
    }

    private static LocalDate toDate(String year, String month, String day) {
        LocalDate date;
        try {
            date = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            date = null;
        }
        return date;
    }
}
