package com.financetracker.ocr.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Receipt OCR settings ({@code app.ocr.*}), see ADR-014 §8.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    /** Tesseract language codes joined by '+'. Needs the matching traineddata files installed. */
    @NotBlank
    private String languages = "vie+eng";

    /** Tesseract page segmentation mode for the first pass (6 = single uniform block of text). */
    @Min(0)
    private int pageSegMode = 6;

    /** Mode for a second pass when the first finds no total (4 = single column); 0 disables the retry. */
    @Min(0)
    private int fallbackPageSegMode = 4;

    /** Maximum time for one Tesseract run. */
    private Duration timeout = Duration.ofSeconds(15);

    /** Receipts read at the same time; Tesseract is CPU-bound, so keep this near the number of cores. */
    @Min(1)
    private int maxConcurrent = 2;

    /** Resolution used to render the first page of a PDF receipt. */
    @Min(72)
    private int pdfDpi = 300;

    /** Whether uploaded receipt images are kept. Only NONE is supported: images are discarded after reading. */
    private String imageRetention = "NONE";

    /** Where to find Tesseract. */
    private Tesseract tesseract = new Tesseract();

    @Getter
    @Setter
    public static class Tesseract {
        /** Executable name or absolute path. */
        @NotBlank
        private String command = "tesseract";

        /** Directory containing *.traineddata; blank uses Tesseract's built-in default. */
        private String tessdataPath = "";
    }
}
