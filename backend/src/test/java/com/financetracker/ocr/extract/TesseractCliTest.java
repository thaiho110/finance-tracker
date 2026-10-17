package com.financetracker.ocr.extract;

import com.financetracker.ocr.exception.OcrErrorCode;
import com.financetracker.ocr.exception.OcrException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real Tesseract binary when it is installed (it is in the Docker image); skipped otherwise.
 * Install locally with e.g. {@code apt install tesseract-ocr tesseract-ocr-vie}.
 */
@DisplayName("TesseractCli")
class TesseractCliTest {

    private static TesseractCli cli(String command) {
        return new TesseractCli(new TesseractCli.Settings(command, "eng", 6, Duration.ofSeconds(30), ""));
    }

    private static BufferedImage receiptImage() {
        BufferedImage img = new BufferedImage(900, 260, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 900, 260);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        g.drawString("CIRCLE K", 40, 60);
        g.drawString("15/10/2026 07:12", 40, 120);
        g.drawString("TONG CONG      60.000", 40, 200);
        g.dispose();
        return img;
    }

    @Test
    @DisplayName("builds the expected command line")
    void buildsCommand() {
        TesseractCli cli = new TesseractCli(new TesseractCli.Settings("tesseract", "vie+eng", 6, Duration.ofSeconds(5), "/data"));

        assertEquals(List.of("tesseract", "--tessdata-dir", "/data", "stdin", "stdout", "-l", "vie+eng", "--psm", "4",
            "--oem", "1", "-c", "preserve_interword_spaces=1"), cli.command(4));
    }

    @Test
    @DisplayName("reports a missing executable instead of hanging")
    void missingExecutable() {
        TesseractCli cli = cli("definitely-not-tesseract-1234");

        assertFalse(cli.isAvailable());
        OcrException e = assertThrows(OcrException.class, () -> cli.recognize(receiptImage()));
        assertEquals(OcrErrorCode.OCR_FAILED, e.getCode());
    }

    @Test
    @DisplayName("reads text from an image with the installed tesseract")
    void readsText() {
        TesseractCli cli = cli("tesseract");
        assumeTrue(cli.isAvailable(), "tesseract is not installed");

        String text = cli.recognize(receiptImage());

        assertNotNull(text);
        assertTrue(text.contains("CIRCLE K"), text);
        assertTrue(text.contains("60.000"), text);
    }
}
