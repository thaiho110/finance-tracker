package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.transaction.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
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

    /**
     * Process a receipt image.
     * The raw image bytes are discarded after processing.
     * A normalized version is generated for potential storage.
     */
    public ParsedReceiptResponse processReceipt(MultipartFile image, UUID userId) {
        try {
            // 1. Validate image
            byte[] imageBytes = image.getBytes();
            if (imageBytes.length == 0) {
                throw new IllegalArgumentException("Receipt image is empty");
            }

            // 2. Normalize image (resize, convert to standard format)
            byte[] normalizedImage = normalizeImage(imageBytes);

            // 3. Extract receipt data (stub — replace with actual OCR implementation)
            // TODO: Implement actual OCR extraction (e.g. Tesseract Tess4J)
            ParsedReceiptResponse response = extractReceiptData(normalizedImage);

            // 4. Check duplicates
            boolean isDuplicate = deduplicationService.isDuplicate(userId, response.date(), response.totalAmount());
            String category = categorizationService.categorize(response.merchant());

            // 5. Raw image bytes are discarded (not stored)
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
     * TODO: Implement actual OCR logic (e.g. Tesseract via Tess4J).
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
