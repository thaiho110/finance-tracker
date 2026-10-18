package com.financetracker.ocr.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReceiptTextParser")
class ReceiptTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
    private static final List<String> KNOWN = List.of("BACH HOA XANH", "CIRCLE K", "HIGHLANDS");

    private final ReceiptTextParser parser = new ReceiptTextParser();

    private ExtractedReceipt parse(String text) {
        return parser.parse(List.of(text.split("\n")), KNOWN, TODAY);
    }

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount);
    }

    @Test
    @DisplayName("reads a typical grocery receipt")
    void readsGroceryReceipt() {
        ExtractedReceipt r = parse("""
            BACH HOA XANH
            CH 2214 - Q.BINH THANH
            17/10/2026  18:42
            --------------------------
            RAU MUONG              12.000
            TRUNG GA 10Q           32.000
            SUA TUOI VINAMILK 1L   36.500
            GAO ST25 2KG           78.000
            NUOC MAM NAM NGU       26.000
            --------------------------
            TONG CONG             184.500
            CAM ON QUY KHACH
            """);

        assertEquals("Bach Hoa Xanh", r.merchant());
        assertEquals(LocalDate.of(2026, 10, 17), r.date());
        assertEquals(vnd(184_500), r.total());
        assertEquals(5, r.lineItems().size());
        assertEquals("RAU MUONG", r.lineItems().get(0).item());
        assertEquals(vnd(36_500), r.lineItems().get(2).price());
        assertTrue(r.warnings().isEmpty(), "warnings: " + r.warnings());
        assertTrue(r.amountsFound());
    }

    @Test
    @DisplayName("matches Vietnamese keywords with diacritics")
    void matchesKeywordsWithDiacritics() {
        ExtractedReceipt r = parse("""
            CỬA HÀNG TIỆN LỢI ABC
            Ngày: 16/10/2026
            Bánh mì thịt            25.000
            Cà phê sữa đá           29.000
            TỔNG CỘNG:              54.000 đ
            """);

        assertEquals("Cửa Hàng Tiện Lợi Abc", r.merchant());
        assertEquals(LocalDate.of(2026, 10, 16), r.date());
        assertEquals(vnd(54_000), r.total());
        assertEquals("Cà phê sữa đá", r.lineItems().get(1).item());
    }

    @Test
    @DisplayName("ignores cash given and change when finding the total")
    void ignoresCashAndChange() {
        ExtractedReceipt r = parse("""
            CIRCLE K VIETNAM
            15/10/2026 07:12
            2 x 12.000             24.000
            Nuoc suoi              10.000
            Tong tien              34.000
            Khach tra             100.000
            Tien thoi lai          66.000
            """);

        assertEquals("Circle K Vietnam", r.merchant());
        assertEquals(vnd(34_000), r.total());
        assertFalse(r.warnings().contains(ReceiptWarnings.TOTAL_GUESSED));
    }

    @Test
    @DisplayName("prefers the final payable amount over the subtotal")
    void prefersPayableOverSubtotal() {
        ExtractedReceipt r = parse("""
            HIGHLANDS COFFEE
            Tra sen vang             45.000
            Banh mi que              19.000
            Tong tien hang           64.000
            Giam gia                  6.400
            Tong thanh toan          57.600
            """);

        assertEquals(vnd(57_600), r.total());
        assertFalse(r.warnings().contains(ReceiptWarnings.ITEMS_DO_NOT_MATCH_TOTAL),
            "discount should explain the difference: " + r.warnings());
    }

    @Test
    @DisplayName("reads the amount from the line below the total label")
    void readsAmountOnNextLine() {
        ExtractedReceipt r = parse("""
            QUAN AN NGON
            TONG CONG
            250,000
            """);

        assertEquals(vnd(250_000), r.total());
    }

    @Test
    @DisplayName("takes the item name from the line above a quantity line")
    void itemNameFromPreviousLine() {
        ExtractedReceipt r = parse("""
            WINMART
            Sua chua Vinamilk hop 4
            2 x 28.000              56.000
            TONG CONG               56.000
            """);

        assertEquals(1, r.lineItems().size());
        assertEquals("Sua chua Vinamilk hop 4", r.lineItems().get(0).item());
        assertEquals(vnd(56_000), r.lineItems().get(0).price());
    }

    @Test
    @DisplayName("guesses the total from the largest amount near the bottom when no keyword is found")
    void guessesTotal() {
        ExtractedReceipt r = parse("""
            TIEM BANH
            Banh bong lan     30.000
            Banh flan         15.000
            Tra da             5.000
            Cong              50.000
            """);

        assertEquals(vnd(50_000), r.total());
        assertTrue(r.warnings().contains(ReceiptWarnings.TOTAL_GUESSED));
    }

    @Test
    @DisplayName("does not read dates, times, phone numbers or codes as amounts")
    void ignoresNonAmountNumbers() {
        ExtractedReceipt r = parse("""
            NHA THUOC LONG CHAU
            DT: 028 3822 1234
            MST: 0312345678
            HD: 00012345  17/10/2026 2026
            """);

        assertNull(r.total());
        assertFalse(r.amountsFound());
        assertTrue(r.warnings().contains(ReceiptWarnings.TOTAL_NOT_FOUND));
    }

    @Test
    @DisplayName("repairs letter O misread as zero inside amounts")
    void repairsLetterO() {
        ExtractedReceipt r = parse("""
            CIRCLE K
            TONG CONG   45.OOO
            """);

        assertEquals(vnd(45_000), r.total());
    }

    @Test
    @DisplayName("rejects a date in the future")
    void rejectsFutureDate() {
        ExtractedReceipt r = parse("""
            CIRCLE K
            01/12/2026
            TONG CONG   45.000
            """);

        assertNull(r.date());
        assertTrue(r.warnings().contains(ReceiptWarnings.DATE_SUSPICIOUS));
    }

    @Test
    @DisplayName("reads ISO dates and two-digit years")
    void readsOtherDateFormats() {
        assertEquals(LocalDate.of(2026, 10, 3), parse("SHOP A\n2026-10-03 10:00\nTONG 10.000").date());
        assertEquals(LocalDate.of(2026, 10, 3), parse("SHOP A\n03/10/26\nTONG 10.000").date());
    }

    @Test
    @DisplayName("fuzzy-matches a known merchant with OCR mistakes")
    void fuzzyMatchesKnownMerchant() {
        ExtractedReceipt r = parse("""
            BACH H0A XANII
            TONG CONG   10.000
            """);

        assertEquals("Bach Hoa Xanh", r.merchant());
    }

    @Test
    @DisplayName("skips address and phone lines when looking for the merchant")
    void skipsHeaderLinesForMerchant() {
        ExtractedReceipt r = parse("""
            Dia chi: 12 Le Loi, Q1
            DT: 0901 234 567
            PHO HUNG
            Pho bo tai               65.000
            TONG CONG                65.000
            """);

        assertEquals("Pho Hung", r.merchant());
    }

    @Test
    @DisplayName("warns when items do not add up to the total")
    void warnsWhenItemsDoNotMatch() {
        ExtractedReceipt r = parse("""
            SHOP B
            Item one     10.000
            Item two     20.000
            TONG CONG    35.000
            """);

        assertTrue(r.warnings().contains(ReceiptWarnings.ITEMS_DO_NOT_MATCH_TOTAL));
    }

    @Test
    @DisplayName("returns warnings instead of failing on empty text")
    void handlesEmptyText() {
        ExtractedReceipt r = parser.parse(List.of("", "   "), KNOWN, TODAY);

        assertNull(r.merchant());
        assertNull(r.date());
        assertNull(r.total());
        assertTrue(r.lineItems().isEmpty());
        assertTrue(r.warnings().containsAll(List.of(
            ReceiptWarnings.MERCHANT_NOT_FOUND, ReceiptWarnings.DATE_NOT_FOUND, ReceiptWarnings.TOTAL_NOT_FOUND)));
    }

    @Test
    @DisplayName("recognizes a misread subtotal label and does not count it as an item")
    void handlesMisreadSubtotal() {
        ExtractedReceipt r = parse("""
            HIGHLANDS COFFEE
            Phin sữa đá              29.000
            Trà sen vàng             45.000
            Bánh mì que              19.000
            Tôäg tiêh hàng           93.000
            Giảm giá                  9.300
            TONG THANH TOÁN          83.700
            """);

        assertEquals(3, r.lineItems().size());
        assertEquals(vnd(83_700), r.total());
        assertFalse(r.warnings().contains(ReceiptWarnings.ITEMS_DO_NOT_MATCH_TOTAL), "warnings: " + r.warnings());
    }

    @Test
    @DisplayName("matches a total keyword with OCR mistakes")
    void fuzzyMatchesTotalKeyword() {
        ExtractedReceipt r = parse("""
            SHOP C
            Item one     10.000
            Item two     20.000
            TONG THANH T0AN    30.000
            Khach dua          50.000
            """);

        assertEquals(vnd(30_000), r.total());
        assertFalse(r.warnings().contains(ReceiptWarnings.TOTAL_GUESSED));
    }
}
