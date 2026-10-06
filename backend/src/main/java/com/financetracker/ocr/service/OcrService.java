package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.transaction.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
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
     * Process a receipt image through the Vision API.
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

            // 3. Call Vision API (placeholder — requires OpenAI API key)
            // TODO: Implement OpenAI Vision API call with normalizedImage
            ParsedReceiptResponse response = callVisionApi(normalizedImage);

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
     * Placeholder for OpenAI Vision API call.
     * TODO: Implement with actual OpenAI client when API key is configured.
     */
    private ParsedReceiptResponse callVisionApi(byte[] normalizedImage) {
        // Encode image to base64 for API call
        String base64Image = Base64.getEncoder().encodeToString(normalizedImage);

        // TODO: Make HTTP call to OpenAI Vision API with gpt-4o-mini
        // For now, return a placeholder
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
