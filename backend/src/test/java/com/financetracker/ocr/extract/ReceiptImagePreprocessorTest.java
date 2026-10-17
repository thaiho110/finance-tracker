package com.financetracker.ocr.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReceiptImagePreprocessor")
class ReceiptImagePreprocessorTest {

    private static final double ANGLE_TOLERANCE = 0.75;

    /** A white receipt with several lines of dark text, optionally on a dark background. */
    private static BufferedImage receipt(boolean darkBackground) {
        BufferedImage img = new BufferedImage(700, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(darkBackground ? new Color(60, 55, 50) : Color.WHITE);
        g.fillRect(0, 0, 700, 600);
        g.setColor(Color.WHITE);
        g.fillRect(100, 40, 500, 520);
        g.setColor(new Color(30, 30, 30));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
        for (int line = 0; line < 14; line++) {
            g.drawString("ITEM NUMBER " + line + "        12.000", 120, 80 + line * 34);
        }
        g.dispose();
        return img;
    }

    @Test
    @DisplayName("detects skew in both directions, also on a dark background")
    void detectsSkew() {
        for (boolean dark : new boolean[] {false, true}) {
            BufferedImage gray = ReceiptImagePreprocessor.toGray(receipt(dark));
            for (double angle : new double[] {-6, -3, 3, 6}) {
                double estimate = ReceiptImagePreprocessor.estimateSkewDegrees(ReceiptImagePreprocessor.rotate(gray, angle));
                assertEquals(angle, estimate, ANGLE_TOLERANCE, "rotation " + angle + " dark=" + dark);
            }
        }
    }

    @Test
    @DisplayName("deskew straightens a rotated receipt")
    void deskewStraightens() {
        BufferedImage rotated = ReceiptImagePreprocessor.rotate(ReceiptImagePreprocessor.toGray(receipt(true)), 4);

        BufferedImage straight = ReceiptImagePreprocessor.deskew(rotated);

        assertEquals(0, ReceiptImagePreprocessor.estimateSkewDegrees(straight), ANGLE_TOLERANCE);
    }

    @Test
    @DisplayName("upscales small photos and returns a grayscale image")
    void upscalesSmallPhotos() {
        BufferedImage small = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);

        BufferedImage processed = new ReceiptImagePreprocessor().process(small);

        assertEquals(BufferedImage.TYPE_BYTE_GRAY, processed.getType());
        assertTrue(processed.getWidth() >= 1300, "width " + processed.getWidth());
    }

    @Test
    @DisplayName("caps the long edge of very large photos")
    void capsLargePhotos() {
        BufferedImage tall = new BufferedImage(1000, 6000, BufferedImage.TYPE_BYTE_GRAY);

        BufferedImage scaled = ReceiptImagePreprocessor.rescale(tall);

        assertTrue(scaled.getHeight() <= 3500, "height " + scaled.getHeight());
    }

    @Test
    @DisplayName("adaptive threshold produces only black and white pixels")
    void thresholdIsBinary() {
        BufferedImage binary = ReceiptImagePreprocessor.adaptiveThreshold(ReceiptImagePreprocessor.toGray(receipt(true)));

        for (int y = 0; y < binary.getHeight(); y += 7) {
            for (int x = 0; x < binary.getWidth(); x += 7) {
                int value = binary.getRaster().getSample(x, y, 0);
                assertTrue(value == 0 || value == 255, "pixel value " + value);
            }
        }
    }
}
