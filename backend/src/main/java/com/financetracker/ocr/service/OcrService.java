package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.transaction.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcrService {

    private final CategorizationService categorizationService;
    private final DeduplicationService deduplicationService;

    @Value("${app.ocr.image-max-width:1024}")
    private int imageMaxWidth;

    @Value("${app.ocr.pdf-dpi:300}")
    private int pdfDpi;

    /**
     * Process a receipt image or PDF.
     * The raw file bytes are discarded after processing.
     * A normalized version is generated for potential storage.
     */
    public ParsedReceiptResponse processReceipt(MultipartFile file, UUID userId) {
        try {
            // 1. Validate file
            byte[] fileBytes = file.getBytes();
            if (fileBytes.length == 0) {
                throw new IllegalArgumentException("Receipt file is empty");
            }

            // 2. Normalize to image (handle PDF if needed)
            byte[] normalizedImage;
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();

            if (isPdf(contentType, filename)) {
                normalizedImage = renderPdfPageToImage(fileBytes);
            } else {
                normalizedImage = normalizeImage(fileBytes);
            }

            // 3. Extract receipt data (stub — replace with actual OCR implementation)
            // TODO: Implement actual OCR extraction (e.g. OpenAI Vision API per ADR-003)
            ParsedReceiptResponse response = extractReceiptData(normalizedImage);

            // 4. Check duplicates
            boolean isDuplicate = deduplicationService.isDuplicate(userId, response.date(), response.totalAmount());
            String category = categorizationService.categorize(response.merchant());

            // 5. Raw file bytes are discarded (not stored)
            // Normalized image could be stored if configured
            // TODO: Add configurable normalized image storage

            return new ParsedReceiptResponse(
                response.merchant(), response.date(), response.totalAmount(),
                category, isDuplicate, response.lineItems());

        } catch (Exception e) {
            throw new RuntimeException("OCR processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether the uploaded file is a PDF based on content type or filename.
     */
    private boolean isPdf(String contentType, String filename) {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return false;
    }

    /**
     * Render the first page of a PDF to a JPEG image at the configured DPI.
     */
    private byte[] renderPdfPageToImage(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            // Render first page at configured DPI for OCR-quality resolution
            BufferedImage pageImage = renderer.renderImageWithDPI(0, pdfDpi);

            // Convert to JPEG bytes via the same normalization pipeline
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(pageImage, "jpg", baos);
            byte[] jpegBytes = baos.toByteArray();

            // Resize if wider than max
            return normalizeImage(jpegBytes);

        } catch (Exception e) {
            throw new RuntimeException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Normalize image to a standard format: JPEG, max width 1024px.
     * This ensures consistent processing regardless of input format.
     */
    private byte[] normalizeImage(byte[] imageBytes) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                throw new IllegalArgumentException("Unable to decode image");
            }

            int width = original.getWidth();
            int height = original.getHeight();

            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Image has invalid dimensions: " + width + "x" + height);
            }

            // Resize if wider than max
            if (width > imageMaxWidth) {
                double ratio = (double) imageMaxWidth / width;
                int newHeight = Math.max(1, (int) (height * ratio));
                BufferedImage resized = new BufferedImage(imageMaxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.drawImage(original, 0, 0, imageMaxWidth, newHeight, null);
                g.dispose();
                original = resized;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(original, "jpg", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Image normalization failed", e);
        }
    }

    /**
     * Placeholder for OCR extraction.
     * TODO: Implement actual OCR logic (e.g. OpenAI Vision API per ADR-003).
     */
    private ParsedReceiptResponse extractReceiptData(byte[] normalizedImage) {
        return new ParsedReceiptResponse(
            "UNKNOWN MERCHANT",
            LocalDate.now(),
            BigDecimal.ZERO,
            "Other",
            false,
            List.of()
        );
    }
}
