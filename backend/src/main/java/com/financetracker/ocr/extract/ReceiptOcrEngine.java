package com.financetracker.ocr.extract;

import java.awt.image.BufferedImage;

/**
 * Reads the text on a receipt image. The production implementation runs Tesseract locally
 * ({@link TesseractReceiptOcrEngine}); tests use a fake that returns fixed text.
 */
public interface ReceiptOcrEngine {

    /** How the engine should assume text is laid out on the page. */
    enum Layout {
        /** The default layout, tried first. */
        PRIMARY,
        /** An alternative layout, tried when the primary pass finds no total. */
        FALLBACK
    }

    /**
     * Recognizes text in a receipt image.
     *
     * @param image  the decoded receipt image (any size or color model)
     * @param layout which layout assumption to use
     * @return the recognized text, one receipt line per text line; {@code null} if this engine has
     *         no {@link Layout#FALLBACK} configured
     * @throws com.financetracker.ocr.exception.OcrException when recognition fails or times out
     */
    String recognize(BufferedImage image, Layout layout);
}
