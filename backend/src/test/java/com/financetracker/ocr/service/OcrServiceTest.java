package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.config.OcrProperties;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.ocr.exception.OcrErrorCode;
import com.financetracker.ocr.exception.OcrException;
import com.financetracker.ocr.extract.ReceiptOcrEngine;
import com.financetracker.ocr.extract.ReceiptOcrEngine.Layout;
import com.financetracker.ocr.extract.ReceiptWarnings;
import com.financetracker.transaction.intake.TransactionIntake;
import com.financetracker.transaction.service.DeduplicationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OcrService Unit Tests")
class OcrServiceTest {

    private static final String GROCERY_RECEIPT = """
        BACH HOA XANH
        CH 2214 - Q.BINH THANH
        17/10/2026  18:42
        RAU MUONG              12.000
        GAO ST25 2KG           78.000
        TONG CONG              90.000
        """;

    @Mock private CategorizationService categorizationService;
    @Mock private DeduplicationService deduplicationService;
    @Mock private ReceiptOcrEngine ocrEngine;

    private SimpleMeterRegistry meterRegistry;
    private OcrService ocrService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        TransactionIntake intake = new TransactionIntake(categorizationService, deduplicationService);
        ocrService = new OcrService(categorizationService, intake, ocrEngine, meterRegistry, new OcrProperties());
        ocrService.setClock(Clock.fixed(Instant.parse("2026-10-18T03:00:00Z"), ZoneOffset.UTC));
        lenient().when(categorizationService.knownMerchantKeywords()).thenReturn(List.of("BACH HOA XANH"));
        lenient().when(categorizationService.categorize(anyString())).thenReturn("Food & Dining");
    }

    private static MockMultipartFile jpeg() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("image", "receipt.jpg", "image/jpeg", out.toByteArray());
    }

    @Test
    @DisplayName("Should throw when receipt file is empty")
    void shouldThrowWhenFileEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> ocrService.processReceipt(emptyFile, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should reject files that are not images or PDFs")
    void shouldRejectUndecodableFile() {
        MockMultipartFile invalidFile = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> ocrService.processReceipt(invalidFile, userId))
            .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getCode()).isEqualTo(OcrErrorCode.UNSUPPORTED_FILE));
    }

    @Test
    @DisplayName("Should extract merchant, date, total, items and category from OCR text")
    void shouldExtractReceiptFields() throws Exception {
        when(ocrEngine.recognize(any(), eq(Layout.PRIMARY))).thenReturn(GROCERY_RECEIPT);
        when(deduplicationService.isDuplicate(userId, LocalDate.of(2026, 10, 17), BigDecimal.valueOf(90_000))).thenReturn(true);

        ParsedReceiptResponse response = ocrService.processReceipt(jpeg(), userId);

        assertThat(response.merchant()).isEqualTo("Bach Hoa Xanh");
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(response.totalAmount()).isEqualByComparingTo("90000");
        assertThat(response.category()).isEqualTo("Food & Dining");
        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.lineItems()).extracting(ParsedReceiptResponse.LineItem::item)
            .containsExactly("RAU MUONG", "GAO ST25 2KG");
        assertThat(response.warnings()).isEmpty();
        verify(categorizationService).categorize("Bach Hoa Xanh");
        verify(ocrEngine, never()).recognize(any(), eq(Layout.FALLBACK));
        assertThat(meterRegistry.counter("finance.ocr.receipts", "outcome", "ok").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should retry with the fallback layout when no total is found, and keep the better result")
    void shouldRetryWithFallbackLayout() throws Exception {
        when(ocrEngine.recognize(any(), eq(Layout.PRIMARY))).thenReturn("BACH HOA XANH\n17/10/2026\nRAU MUONG\nTONG CONG\n");
        when(ocrEngine.recognize(any(), eq(Layout.FALLBACK))).thenReturn(GROCERY_RECEIPT);

        ParsedReceiptResponse response = ocrService.processReceipt(jpeg(), userId);

        assertThat(response.totalAmount()).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("Should return nulls and warnings instead of fake values when fields are missing")
    void shouldReturnWarningsForMissingFields() throws Exception {
        when(ocrEngine.recognize(any(), eq(Layout.PRIMARY))).thenReturn("some faded text here\nonly an amount 45.000\n");

        ParsedReceiptResponse response = ocrService.processReceipt(jpeg(), userId);

        assertThat(response.date()).isNull();
        assertThat(response.warnings()).contains(ReceiptWarnings.DATE_NOT_FOUND, ReceiptWarnings.TOTAL_GUESSED);
        verify(deduplicationService, never()).isDuplicate(any(), any(), any());
    }

    @Test
    @DisplayName("Should fail with UNREADABLE when OCR finds almost no text")
    void shouldFailWhenUnreadable() throws Exception {
        when(ocrEngine.recognize(any(), any())).thenReturn("~ ..  ' ");

        assertThatThrownBy(() -> ocrService.processReceipt(jpeg(), userId))
            .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getCode()).isEqualTo(OcrErrorCode.UNREADABLE));
        assertThat(meterRegistry.counter("finance.ocr.receipts", "outcome", "unreadable").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should fail with NOT_A_RECEIPT when the text contains no amounts")
    void shouldFailWhenNoAmounts() throws Exception {
        when(ocrEngine.recognize(any(), any())).thenReturn("Dear diary, today I went to the park with friends.");

        assertThatThrownBy(() -> ocrService.processReceipt(jpeg(), userId))
            .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getCode()).isEqualTo(OcrErrorCode.NOT_A_RECEIPT));
    }

    @Test
    @DisplayName("Should pass OCR engine failures through with their code")
    void shouldPropagateEngineTimeout() throws Exception {
        when(ocrEngine.recognize(any(), any())).thenThrow(new OcrException(OcrErrorCode.OCR_TIMEOUT, "too slow"));

        assertThatThrownBy(() -> ocrService.processReceipt(jpeg(), userId))
            .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.getCode()).isEqualTo(OcrErrorCode.OCR_TIMEOUT));
    }

    @Test
    @DisplayName("Should render the first page of a PDF receipt")
    void shouldReadPdf() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            pdf = out.toByteArray();
        }
        when(ocrEngine.recognize(any(), eq(Layout.PRIMARY))).thenReturn(GROCERY_RECEIPT);

        ParsedReceiptResponse response = ocrService.processReceipt(
            new MockMultipartFile("image", "receipt.pdf", "application/pdf", pdf), userId);

        assertThat(response.totalAmount()).isEqualByComparingTo("90000");
    }
}
