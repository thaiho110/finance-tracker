package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.config.OcrProperties;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.ocr.exception.OcrErrorCode;
import com.financetracker.ocr.exception.OcrException;
import com.financetracker.ocr.extract.ExtractedReceipt;
import com.financetracker.ocr.extract.ReceiptOcrEngine;
import com.financetracker.ocr.extract.ReceiptTextParser;
import com.financetracker.transaction.service.DeduplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reads receipts (ADR-014): decode the upload (image or first PDF page) → local OCR → rule-based
 * parsing → categorization and duplicate check. The uploaded bytes are never stored.
 */
@Service
public class OcrService {

    /** Fewer letters/digits than this means OCR could not read the photo. */
    private static final int MIN_TEXT_CHARACTERS = 20;
    private static final ZoneId RECEIPT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String METRIC_RECEIPTS = "finance.ocr.receipts";
    private static final String METRIC_DURATION = "finance.ocr.duration";
    private static final String METRIC_FIELDS = "finance.ocr.fields.found";
    private static final String METRIC_WARNINGS = "finance.ocr.warnings";

    private final CategorizationService categorizationService;
    private final DeduplicationService deduplicationService;
    private final ReceiptOcrEngine ocrEngine;
    private final MeterRegistry meterRegistry;
    private final OcrProperties properties;
    private final ReceiptTextParser parser = new ReceiptTextParser();
    private Clock clock = Clock.system(RECEIPT_ZONE);

    public OcrService(CategorizationService categorizationService,
                      DeduplicationService deduplicationService,
                      ReceiptOcrEngine ocrEngine,
                      MeterRegistry meterRegistry,
                      OcrProperties properties) {
        this.categorizationService = categorizationService;
        this.deduplicationService = deduplicationService;
        this.ocrEngine = ocrEngine;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    /** For tests: fixes "today", which the parser uses to reject implausible receipt dates. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Process a receipt image or PDF and extract transaction data.
     *
     * @throws IllegalArgumentException when the upload is empty
     * @throws OcrException             when the file can't be decoded or the receipt can't be read
     */
    public ParsedReceiptResponse processReceipt(MultipartFile file, UUID userId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            BufferedImage image = decode(file);
            ExtractedReceipt receipt = read(image);
            ParsedReceiptResponse response = toResponse(receipt, userId);
            recordFields(receipt);
            outcome = "ok";
            return response;
        } catch (OcrException e) {
            outcome = e.getCode().name().toLowerCase(Locale.ROOT);
            throw e;
        } finally {
            meterRegistry.counter(METRIC_RECEIPTS, "outcome", outcome).increment();
            sample.stop(meterRegistry.timer(METRIC_DURATION, "outcome", outcome));
        }
    }

    // ───────────────────────── decoding ─────────────────────────

    private BufferedImage decode(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new OcrException(OcrErrorCode.UNSUPPORTED_FILE, "Could not read the uploaded file", e);
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Receipt file is empty");
        }
        BufferedImage image = isPdf(file.getContentType(), file.getOriginalFilename())
            ? renderFirstPdfPage(bytes)
            : readImage(bytes);
        if (image == null) {
            throw new OcrException(OcrErrorCode.UNSUPPORTED_FILE, "Use a JPG, PNG or PDF receipt");
        }
        return image;
    }

    private static boolean isPdf(String contentType, String filename) {
        boolean pdfType = contentType != null && contentType.equalsIgnoreCase("application/pdf");
        boolean pdfName = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return pdfType || pdfName;
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new OcrException(OcrErrorCode.UNSUPPORTED_FILE, "The image could not be decoded", e);
        }
    }

    private BufferedImage renderFirstPdfPage(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new OcrException(OcrErrorCode.UNSUPPORTED_FILE, "The PDF has no pages");
            }
            return new PDFRenderer(document).renderImageWithDPI(0, properties.getPdfDpi(), ImageType.GRAY);
        } catch (IOException e) {
            throw new OcrException(OcrErrorCode.UNSUPPORTED_FILE, "The PDF could not be read", e);
        }
    }

    // ───────────────────────── OCR + parsing ─────────────────────────

    private ExtractedReceipt read(BufferedImage image) {
        List<String> knownMerchants = categorizationService.knownMerchantKeywords();
        String text = nullToEmpty(ocrEngine.recognize(image, ReceiptOcrEngine.Layout.PRIMARY));
        ExtractedReceipt receipt = parse(text, knownMerchants);

        if (receipt.total() == null) {
            String retryText = ocrEngine.recognize(image, ReceiptOcrEngine.Layout.FALLBACK);
            if (retryText != null) {
                ExtractedReceipt retry = parse(retryText, knownMerchants);
                if (fieldsFound(retry) > fieldsFound(receipt)) {
                    receipt = retry;
                    text = retryText;
                }
            }
        }

        if (countTextCharacters(text) < MIN_TEXT_CHARACTERS) {
            throw new OcrException(OcrErrorCode.UNREADABLE, "Could not read this receipt. Try a clearer photo.");
        }
        if (!receipt.amountsFound()) {
            throw new OcrException(OcrErrorCode.NOT_A_RECEIPT, "No amounts were found, so this doesn't look like a receipt.");
        }
        return receipt;
    }

    private ExtractedReceipt parse(String text, List<String> knownMerchants) {
        return parser.parse(List.of(text.split("\\R")), knownMerchants, LocalDate.now(clock));
    }

    private static int fieldsFound(ExtractedReceipt receipt) {
        int found = 0;
        found += receipt.merchant() != null ? 1 : 0;
        found += receipt.date() != null ? 1 : 0;
        found += receipt.total() != null ? 1 : 0;
        found += receipt.lineItems().isEmpty() ? 0 : 1;
        return found;
    }

    private static int countTextCharacters(String text) {
        return (int) text.chars().filter(Character::isLetterOrDigit).count();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    // ───────────────────────── response ─────────────────────────

    private ParsedReceiptResponse toResponse(ExtractedReceipt receipt, UUID userId) {
        String category = categorizationService.categorize(receipt.merchant());
        boolean isDuplicate = receipt.date() != null && receipt.total() != null
            && deduplicationService.isDuplicate(userId, receipt.date(), receipt.total());
        List<ParsedReceiptResponse.LineItem> lineItems = receipt.lineItems().stream()
            .map(item -> new ParsedReceiptResponse.LineItem(item.item(), item.price()))
            .toList();
        return new ParsedReceiptResponse(receipt.merchant(), receipt.date(), receipt.total(),
            category, isDuplicate, lineItems, receipt.warnings());
    }

    private void recordFields(ExtractedReceipt receipt) {
        countField("merchant", receipt.merchant() != null);
        countField("date", receipt.date() != null);
        countField("total", receipt.total() != null);
        countField("line_items", !receipt.lineItems().isEmpty());
        receipt.warnings().forEach(code -> meterRegistry.counter(METRIC_WARNINGS, "code", code).increment());
    }

    private void countField(String field, boolean found) {
        meterRegistry.counter(METRIC_FIELDS, "field", field, "found", String.valueOf(found)).increment();
    }
}
