package com.financetracker.ocr.extract;

import com.financetracker.ocr.config.OcrProperties;
import com.financetracker.ocr.exception.OcrErrorCode;
import com.financetracker.ocr.exception.OcrException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Receipt OCR with a locally installed Tesseract (ADR-014): cleans the image with
 * {@link ReceiptImagePreprocessor}, then runs the {@code tesseract} CLI.
 * Tesseract is CPU-bound, so at most {@code app.ocr.max-concurrent} recognitions run at once;
 * a request that cannot get a slot within the OCR timeout fails with {@link OcrErrorCode#OCR_BUSY}.
 */
@Slf4j
@Component
public class TesseractReceiptOcrEngine implements ReceiptOcrEngine {

    private final TesseractCli tesseract;
    private final ReceiptImagePreprocessor preprocessor = new ReceiptImagePreprocessor();
    private final Semaphore slots;
    private final int fallbackPageSegMode;

    public TesseractReceiptOcrEngine(OcrProperties properties) {
        this.tesseract = new TesseractCli(new TesseractCli.Settings(
            properties.getTesseract().getCommand(),
            properties.getLanguages(),
            properties.getPageSegMode(),
            properties.getTimeout(),
            properties.getTesseract().getTessdataPath()));
        this.slots = new Semaphore(properties.getMaxConcurrent(), true);
        this.fallbackPageSegMode = properties.getFallbackPageSegMode();
    }

    @Override
    public String recognize(BufferedImage image, Layout layout) {
        int mode = layout == Layout.FALLBACK ? fallbackPageSegMode : tesseract.settings().pageSegMode();
        if (mode <= 0) {
            return null;
        }
        acquireSlot();
        try {
            return tesseract.recognize(preprocessor.process(image), mode);
        } finally {
            slots.release();
        }
    }

    /** True when the Tesseract executable can be started. */
    public boolean isAvailable() {
        return tesseract.isAvailable();
    }

    /** Logs a clear warning at startup if receipts can't be read, instead of failing on the first upload. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkInstallation() {
        if (isAvailable()) {
            log.info("Receipt OCR ready: tesseract, languages={}", tesseract.settings().languages());
        } else {
            log.warn("Receipt OCR unavailable: '{}' could not be started. Install tesseract-ocr with the {} "
                + "language data, or set app.ocr.tesseract.command.", tesseract.settings().command(),
                tesseract.settings().languages());
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(tesseract.settings().timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new OcrException(OcrErrorCode.OCR_BUSY, "Too many receipts are being read right now");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException(OcrErrorCode.OCR_FAILED, "Interrupted while waiting to read the receipt", e);
        }
    }
}
