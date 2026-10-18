package com.financetracker.ocr.extract;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns OCR text from a Vietnamese retail/F&amp;B receipt into structured fields using plain rules
 * (ADR-014 §4). Pure and deterministic: no I/O, no Spring, "today" is passed in.
 */
// A rule-based parser is a flat pile of small, independent checks; its class-total branch count is high
// by nature while every individual method stays simple, so the class-level complexity cap doesn't fit it.
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class ReceiptTextParser {

    /** Total keywords, most specific first; the first phrase a line contains decides its priority. */
    private static final List<TotalRule> TOTAL_RULES = List.of(
        new TotalRule("TONG TIEN HANG", 1),
        new TotalRule("TONG THANH TOAN", 5),
        new TotalRule("KHACH PHAI TRA", 5),
        new TotalRule("CAN THANH TOAN", 5),
        new TotalRule("TONG CONG", 4),
        new TotalRule("TONG TIEN", 4),
        new TotalRule("TONG", 3),
        new TotalRule("TOTAL", 3),
        new TotalRule("THANH TOAN", 2),
        new TotalRule("THANH TIEN", 1));

    /** Lines with these phrases hold other amounts (cash given, change, discounts, tax). */
    private static final List<String> NOT_TOTAL = List.of(
        "KHACH TRA", "KHACH DUA", "TIEN KHACH", "TIEN MAT", "TIEN THOI", "TRA LAI", "THOI LAI",
        "GIAM GIA", "CHIET KHAU", "KHUYEN MAI", "VAT", "THUE", "CASH", "CHANGE", "DISCOUNT");

    /** Discount lines: their amounts are subtracted when checking that items add up. */
    private static final List<String> DISCOUNT = List.of("GIAM GIA", "CHIET KHAU", "KHUYEN MAI", "DISCOUNT");

    /** Header/footer lines that are never a merchant name or a line item. */
    private static final List<String> NOT_MERCHANT = List.of(
        "DC", "D C", "DIA CHI", "DT", "DIEN THOAI", "TEL", "SDT", "HOTLINE", "MST", "MA SO THUE",
        "WWW", "HTTP", "COM", "VN", "EMAIL", "HOA DON", "PHIEU", "RECEIPT", "INVOICE", "SO HD",
        "NGAY", "GIO", "QUAY", "THU NGAN", "NHAN VIEN", "CAM ON", "THANK", "WIFI", "PASS");

    private static final Pattern DMY = Pattern.compile("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern YMD = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern NON_NAME_TOKEN = Pattern.compile(
        "(?i)[\\dOo.,:/x×*@%-]+|SL|VND|VNĐ|Đ|D|₫|KG|G|ML|L|CAI|HOP|CHAI|GOI");
    private static final Pattern LEADING_INDEX = Pattern.compile("^\\s*\\d{1,3}[.)]?\\s+(?=\\D)");
    private static final Pattern EDGE_JUNK = Pattern.compile("^[^\\p{L}\\d]+|[^\\p{L}\\d)]+$");

    private static final int MERCHANT_SEARCH_LINES = 6;
    private static final int MAX_MERCHANT_LENGTH = 60;
    private static final double MIN_MERCHANT_SIMILARITY = 0.8;
    /** Total keywords at least this long also match with OCR mistakes ("TÔÄG TIÊH HÀNG" → "TONG TIEN HANG"). */
    private static final int FUZZY_KEYWORD_MIN_LENGTH = 9;
    private static final double MIN_KEYWORD_SIMILARITY = 0.8;
    private static final int MIN_ITEMS_FOR_SUBTOTAL = 2;
    private static final double MAX_DIGIT_SHARE_IN_NAME = 0.3;
    private static final int TWO_DIGIT_YEAR_BASE = 2000;
    private static final int MAX_DATE_AGE_YEARS = 1;
    private static final int FALLBACK_REGION_DIVISOR = 3;

    private record TotalRule(String phrase, int priority) { }

    private record TotalHit(int lineIndex, BigDecimal amount, boolean guessed) { }

    /**
     * Parse OCR text lines.
     *
     * @param rawLines        lines as returned by OCR (blank lines are ignored)
     * @param knownMerchants  merchant keywords already known to the app (e.g. from category mappings)
     * @param today           today's date, used to reject implausible receipt dates
     * @return extracted fields and warnings; never null
     */
    public ExtractedReceipt parse(List<String> rawLines, Collection<String> knownMerchants, LocalDate today) {
        List<ReceiptLine> lines = rawLines.stream()
            .filter(l -> l != null && !l.isBlank())
            .map(ReceiptLine::new)
            .toList();
        List<String> warnings = new ArrayList<>();

        String merchant = findMerchant(lines, knownMerchants);
        if (merchant == null) {
            warnings.add(ReceiptWarnings.MERCHANT_NOT_FOUND);
        }
        LocalDate date = findDate(lines, today, warnings);
        TotalHit total = findTotal(lines);
        if (total == null) {
            warnings.add(ReceiptWarnings.TOTAL_NOT_FOUND);
        } else if (total.guessed()) {
            warnings.add(ReceiptWarnings.TOTAL_GUESSED);
        }
        int itemsEnd = total == null ? lines.size() : total.lineIndex();
        List<ExtractedReceipt.LineItem> items = findLineItems(lines, itemsEnd);
        if (total != null && !items.isEmpty() && !itemsMatchTotal(items, lines, total.amount())) {
            warnings.add(ReceiptWarnings.ITEMS_DO_NOT_MATCH_TOTAL);
        }
        boolean amountsFound = lines.stream().anyMatch(ReceiptLine::hasAmount);
        return new ExtractedReceipt(merchant, date, total == null ? null : total.amount(), items, warnings, amountsFound);
    }

    // ───────────────────────── merchant ─────────────────────────

    private String findMerchant(List<ReceiptLine> lines, Collection<String> knownMerchants) {
        int limit = Math.min(MERCHANT_SEARCH_LINES, lines.size());
        for (int i = 0; i < limit; i++) {
            String known = matchKnownMerchant(lines.get(i), knownMerchants);
            if (known != null) {
                return known;
            }
        }
        for (int i = 0; i < limit; i++) {
            ReceiptLine line = lines.get(i);
            if (looksLikeName(line)) {
                return titleCase(clean(line.original()));
            }
        }
        return null;
    }

    private String matchKnownMerchant(ReceiptLine line, Collection<String> knownMerchants) {
        String text = line.normalized().replaceAll("[^A-Z0-9]+", " ").strip();
        for (String keyword : knownMerchants) {
            String key = ReceiptLine.normalize(keyword).replaceAll("[^A-Z0-9]+", " ").strip();
            if (key.isEmpty()) {
                continue;
            }
            if (line.containsPhrase(key)) {
                return titleCase(clean(line.original()));
            }
            if (bestWindowSimilarity(text, key) >= MIN_MERCHANT_SIMILARITY) {
                return titleCase(keyword.strip());
            }
        }
        return null;
    }

    private boolean looksLikeName(ReceiptLine line) {
        if (!line.hasText() || line.containsAny(NOT_MERCHANT) || line.hasAmount()) {
            return false;
        }
        String text = line.original();
        long digits = text.chars().filter(Character::isDigit).count();
        return digits <= text.length() * MAX_DIGIT_SHARE_IN_NAME && text.length() <= MAX_MERCHANT_LENGTH;
    }

    /** Best similarity between {@code key} and any same-length window of {@code text}. */
    static double bestWindowSimilarity(String text, String key) {
        if (text.length() < key.length()) {
            return similarity(text, key);
        }
        double best = 0;
        for (int start = 0; start + key.length() <= text.length(); start++) {
            best = Math.max(best, similarity(text.substring(start, start + key.length()), key));
        }
        return best;
    }

    static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0 : 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // ───────────────────────── date ─────────────────────────

    private LocalDate findDate(List<ReceiptLine> lines, LocalDate today, List<String> warnings) {
        LocalDate suspicious = null;
        for (ReceiptLine line : lines) {
            LocalDate date = parseDate(line.original());
            if (date == null) {
                continue;
            }
            boolean plausible = !date.isAfter(today) && !date.isBefore(today.minusYears(MAX_DATE_AGE_YEARS));
            if (plausible) {
                return date;
            }
            suspicious = date;
        }
        warnings.add(suspicious == null ? ReceiptWarnings.DATE_NOT_FOUND : ReceiptWarnings.DATE_SUSPICIOUS);
        return null;
    }

    private static LocalDate parseDate(String text) {
        Matcher ymd = YMD.matcher(text);
        if (ymd.find()) {
            LocalDate date = toDate(ymd.group(1), ymd.group(2), ymd.group(3));
            if (date != null) {
                return date;
            }
        }
        Matcher dmy = DMY.matcher(text);
        while (dmy.find()) {
            String year = dmy.group(3).length() == 2
                ? String.valueOf(TWO_DIGIT_YEAR_BASE + Integer.parseInt(dmy.group(3)))
                : dmy.group(3);
            LocalDate date = toDate(year, dmy.group(2), dmy.group(1));
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private static LocalDate toDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    // ───────────────────────── total ─────────────────────────

    private TotalHit findTotal(List<ReceiptLine> lines) {
        TotalHit best = null;
        int bestPriority = 0;
        for (int i = 0; i < lines.size(); i++) {
            ReceiptLine line = lines.get(i);
            int priority = totalPriority(line);
            if (priority == 0 || priority < bestPriority) {
                continue;
            }
            BigDecimal amount = amountOnOrAfter(lines, i);
            if (amount != null) {
                best = new TotalHit(i, amount, false);
                bestPriority = priority;
            }
        }
        return best != null ? best : guessTotal(lines);
    }

    private static int totalPriority(ReceiptLine line) {
        if (line.containsAny(NOT_TOTAL)) {
            return 0;
        }
        String text = line.normalized().replaceAll("[^A-Z0-9]+", " ").strip();
        for (TotalRule rule : TOTAL_RULES) {
            boolean fuzzy = rule.phrase().length() >= FUZZY_KEYWORD_MIN_LENGTH
                && bestWindowSimilarity(text, rule.phrase()) >= MIN_KEYWORD_SIMILARITY;
            if (line.containsPhrase(rule.phrase()) || fuzzy) {
                return rule.priority();
            }
        }
        return 0;
    }

    /** The amount on a total line, or on the next line when the amount was printed below the label. */
    private static BigDecimal amountOnOrAfter(List<ReceiptLine> lines, int index) {
        ReceiptLine line = lines.get(index);
        if (line.hasAmount()) {
            return line.lastAmount();
        }
        if (index + 1 < lines.size()) {
            ReceiptLine next = lines.get(index + 1);
            if (next.hasAmount() && !next.containsAny(NOT_TOTAL) && !next.hasText()) {
                return next.lastAmount();
            }
        }
        return null;
    }

    /** No keyword: take the largest amount in the bottom third, skipping cash/change/discount lines. */
    private static TotalHit guessTotal(List<ReceiptLine> lines) {
        TotalHit best = null;
        int start = lines.size() - Math.max(1, lines.size() / FALLBACK_REGION_DIVISOR);
        for (int i = Math.max(0, start); i < lines.size(); i++) {
            ReceiptLine line = lines.get(i);
            if (line.hasAmount() && !line.containsAny(NOT_TOTAL)
                && (best == null || line.lastAmount().compareTo(best.amount()) > 0)) {
                best = new TotalHit(i, line.lastAmount(), true);
            }
        }
        return best;
    }

    // ───────────────────────── line items ─────────────────────────

    private List<ExtractedReceipt.LineItem> findLineItems(List<ReceiptLine> lines, int end) {
        List<ExtractedReceipt.LineItem> items = new ArrayList<>();
        String pendingName = null;
        for (int i = 0; i < end; i++) {
            ReceiptLine line = lines.get(i);
            if (line.containsAny(NOT_MERCHANT) || line.containsAny(NOT_TOTAL) || totalPriority(line) > 0) {
                pendingName = null;
                continue;
            }
            if (!line.endsWithAmount()) {
                pendingName = line.hasText() ? clean(line.original()) : pendingName;
                continue;
            }
            String name = itemName(line.original());
            if (name.isEmpty()) {
                name = pendingName;
            }
            if (name != null && !name.isEmpty() && !isSubtotal(items, line.lastAmount())) {
                items.add(new ExtractedReceipt.LineItem(name, line.lastAmount()));
            }
            pendingName = null;
        }
        return items;
    }

    /** A line whose amount equals the sum of the items above it is a subtotal, even if its label was misread. */
    private static boolean isSubtotal(List<ExtractedReceipt.LineItem> items, BigDecimal amount) {
        if (items.size() < MIN_ITEMS_FOR_SUBTOTAL) {
            return false;
        }
        BigDecimal sum = items.stream().map(ExtractedReceipt.LineItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.compareTo(amount) == 0;
    }

    /** Text of a line without the trailing quantity / unit price / price / currency tokens. */
    private static String itemName(String text) {
        String withoutIndex = LEADING_INDEX.matcher(text).replaceFirst("");
        List<String> tokens = new ArrayList<>(List.of(withoutIndex.strip().split("\\s+")));
        while (!tokens.isEmpty() && NON_NAME_TOKEN.matcher(tokens.get(tokens.size() - 1)).matches()) {
            tokens.remove(tokens.size() - 1);
        }
        return clean(String.join(" ", tokens));
    }

    private static boolean itemsMatchTotal(List<ExtractedReceipt.LineItem> items, List<ReceiptLine> lines, BigDecimal total) {
        BigDecimal sum = items.stream().map(ExtractedReceipt.LineItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) == 0) {
            return true;
        }
        BigDecimal discounts = lines.stream()
            .filter(l -> l.containsAny(DISCOUNT) && l.hasAmount())
            .map(ReceiptLine::lastAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.subtract(discounts).compareTo(total) == 0;
    }

    // ───────────────────────── text helpers ─────────────────────────

    private static String clean(String text) {
        return EDGE_JUNK.matcher(text.replaceAll("\\s+", " ")).replaceAll("").strip();
    }

    /** "BÁCH HÓA XANH" → "Bách Hóa Xanh"; single letters ("K") and tokens with digits are kept as they are. */
    static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            boolean keepAsIs = word.length() <= 1 || word.chars().anyMatch(Character::isDigit);
            out.append(keepAsIs ? word
                : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
