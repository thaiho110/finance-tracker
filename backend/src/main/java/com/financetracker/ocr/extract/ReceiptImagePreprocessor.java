package com.financetracker.ocr.extract;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

/**
 * Cleans up a receipt photo before OCR (ADR-014 §2): grayscale → rescale → light denoise → deskew.
 * Plain Java2D, no native dependencies.
 *
 * <p>The result is deliberately left in grayscale. Tesseract binarizes internally, and on test receipts
 * an extra adaptive threshold here made bold digits merge ("184.500" read as "184.5080"). Adaptive
 * thresholding is still used internally to find the skew angle.
 */
public final class ReceiptImagePreprocessor {

    /** Receipts are narrow; at this width a 40-character line gives Tesseract ~25 px tall text. */
    private static final int TARGET_WIDTH = 1400;
    private static final int MAX_LONG_EDGE = 3500;
    /** Bounds memory and OCR time for very large photos. */
    private static final int MAX_PIXELS = 8_000_000;

    private static final int DESKEW_SAMPLE_WIDTH = 400;
    private static final double MAX_SKEW_DEGREES = 10.0;
    private static final double SKEW_STEP_DEGREES = 0.5;
    private static final double MIN_SKEW_TO_FIX = 0.4;
    private static final double ANGLE_EPSILON = 1e-9;

    /** Bradley adaptive threshold: a pixel is ink if it is this much darker than its neighbourhood mean. */
    private static final double THRESHOLD_SENSITIVITY = 0.15;
    private static final int WINDOW_DIVISOR = 32;
    private static final int MIN_WINDOW = 15;

    private static final int WHITE = 255;
    private static final int BYTE_MASK = 0xFF;
    private static final int MEDIAN_SIZE = 9;
    private static final int MEDIAN_INDEX = 4;

    public BufferedImage process(BufferedImage source) {
        BufferedImage gray = rescale(toGray(source));
        BufferedImage denoised = median3x3(gray);
        return deskew(denoised);
    }

    static BufferedImage toGray(BufferedImage source) {
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, gray.getWidth(), gray.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    static BufferedImage rescale(BufferedImage gray) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        double scale = (double) TARGET_WIDTH / width;
        scale = Math.min(scale, (double) MAX_LONG_EDGE / Math.max(width, height));
        scale = Math.min(scale, Math.sqrt((double) MAX_PIXELS / ((double) width * height)));
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        if (newWidth == width && newHeight == height) {
            return gray;
        }
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(gray, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    /** 3×3 median filter — removes thermal-paper speckle without blurring strokes. */
    static BufferedImage median3x3(BufferedImage gray) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        byte[] in = pixels(gray);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] out = pixels(result);
        int[] window = new int[MEDIAN_SIZE];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.clamp(y + dy, 0, height - 1);
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.clamp(x + dx, 0, width - 1);
                        window[n++] = in[yy * width + xx] & BYTE_MASK;
                    }
                }
                Arrays.sort(window);
                out[y * width + x] = (byte) window[MEDIAN_INDEX];
            }
        }
        return result;
    }

    /** Rotates the image so text lines are horizontal (searches ±10°). */
    static BufferedImage deskew(BufferedImage gray) {
        double angle = estimateSkewDegrees(gray);
        if (Math.abs(angle) < MIN_SKEW_TO_FIX) {
            return gray;
        }
        return rotate(gray, -angle);
    }

    /**
     * Estimates text skew with a projection profile: for each candidate angle, ink pixels are projected
     * onto rotated rows; straight text gives the "spikiest" row histogram.
     *
     * @return skew in degrees (positive = text rises to the right is corrected by rotating by -angle)
     */
    static double estimateSkewDegrees(BufferedImage gray) {
        BufferedImage scaled = gray.getWidth() > DESKEW_SAMPLE_WIDTH ? scaleToWidth(gray, DESKEW_SAMPLE_WIDTH) : gray;
        // Local thresholding keeps only text and paper edges as ink; a global threshold would also count a
        // dark table or background behind the receipt, which hides the text lines from the projection.
        BufferedImage sample = adaptiveThreshold(scaled);
        int width = sample.getWidth();
        int height = sample.getHeight();
        byte[] px = pixels(sample);
        int[] inkX = new int[px.length];
        int[] inkY = new int[px.length];
        int inkCount = 0;
        for (int i = 0; i < px.length; i++) {
            if (px[i] == 0) {
                inkX[inkCount] = i % width;
                inkY[inkCount] = i / width;
                inkCount++;
            }
        }
        double bestAngle = 0;
        double bestScore = -1;
        for (double angle = -MAX_SKEW_DEGREES; angle <= MAX_SKEW_DEGREES + ANGLE_EPSILON; angle += SKEW_STEP_DEGREES) {
            double score = projectionScore(inkX, inkY, inkCount, Math.toRadians(angle), width, height);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    private static double projectionScore(int[] xs, int[] ys, int count, double radians, int width, int height) {
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        int offset = width + height;
        int[] rows = new int[2 * offset + 1];
        for (int i = 0; i < count; i++) {
            int row = (int) Math.round(ys[i] * cos - xs[i] * sin) + offset;
            rows[row]++;
        }
        double score = 0;
        for (int i = 1; i < rows.length; i++) {
            double diff = rows[i] - rows[i - 1];
            score += diff * diff;
        }
        return score;
    }

    static BufferedImage rotate(BufferedImage gray, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int width = gray.getWidth();
        int height = gray.getHeight();
        int newWidth = (int) Math.ceil(width * cos + height * sin);
        int newHeight = (int) Math.ceil(height * cos + width * sin);
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = rotated.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate((newWidth - width) / 2.0, (newHeight - height) / 2.0);
        g.rotate(radians, width / 2.0, height / 2.0);
        g.drawImage(gray, 0, 0, null);
        g.dispose();
        return rotated;
    }

    /**
     * Bradley–Roth adaptive threshold using an integral image; output is pure black/white.
     * Only used on the small deskew sample, so the int integral image cannot overflow.
     */
    static BufferedImage adaptiveThreshold(BufferedImage gray) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        byte[] in = pixels(gray);
        int[] integral = new int[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            int rowSum = 0;
            for (int x = 0; x < width; x++) {
                rowSum += in[y * width + x] & BYTE_MASK;
                integral[(y + 1) * (width + 1) + x + 1] = integral[y * (width + 1) + x + 1] + rowSum;
            }
        }
        int half = Math.max(MIN_WINDOW, width / WINDOW_DIVISOR) / 2;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] out = pixels(result);
        for (int y = 0; y < height; y++) {
            int y0 = Math.max(0, y - half);
            int y1 = Math.min(height - 1, y + half);
            for (int x = 0; x < width; x++) {
                int x0 = Math.max(0, x - half);
                int x1 = Math.min(width - 1, x + half);
                long area = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
                long sum = (long) integral[(y1 + 1) * (width + 1) + x1 + 1] - integral[y0 * (width + 1) + x1 + 1]
                    - integral[(y1 + 1) * (width + 1) + x0] + integral[y0 * (width + 1) + x0];
                boolean ink = (long) (in[y * width + x] & BYTE_MASK) * area < sum * (1.0 - THRESHOLD_SENSITIVITY);
                out[y * width + x] = (byte) (ink ? 0 : WHITE);
            }
        }
        return result;
    }

    private static BufferedImage scaleToWidth(BufferedImage gray, int targetWidth) {
        int targetHeight = Math.max(1, gray.getHeight() * targetWidth / gray.getWidth());
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(gray, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }


    private static byte[] pixels(BufferedImage gray) {
        return ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
    }
}
