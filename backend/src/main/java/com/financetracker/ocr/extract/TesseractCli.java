package com.financetracker.ocr.extract;

import com.financetracker.ocr.exception.OcrErrorCode;
import com.financetracker.ocr.exception.OcrException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs the {@code tesseract} command-line program on an image (ADR-014 §3).
 * The image is piped through stdin and text read from stdout, so no temp files are written.
 * Each call starts its own process, which makes this class thread-safe; callers bound concurrency.
 */
public final class TesseractCli {

    private static final int MAX_ERROR_CHARS = 300;
    private static final long VERSION_CHECK_SECONDS = 10;

    private final Settings settings;

    /**
     * @param command        executable name or path, e.g. {@code tesseract}
     * @param languages      language codes joined by '+', e.g. {@code vie+eng}
     * @param pageSegMode    Tesseract page segmentation mode (4 = single column of variable-size text)
     * @param timeout        maximum time for one recognition
     * @param tessdataDir    optional directory with .traineddata files; blank to use Tesseract's default
     */
    public record Settings(String command, String languages, int pageSegMode, Duration timeout, String tessdataDir) { }

    public TesseractCli(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    /** Recognizes text with the configured page segmentation mode. */
    public String recognize(BufferedImage image) {
        return recognize(image, settings.pageSegMode());
    }

    /**
     * Recognizes text in {@code image}.
     *
     * @throws OcrException with {@link OcrErrorCode#OCR_TIMEOUT} or {@link OcrErrorCode#OCR_FAILED}
     */
    public String recognize(BufferedImage image, int pageSegMode) {
        byte[] png = toPng(image);
        Process process = start(command(pageSegMode));
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> writeAndClose(process.getOutputStream(), png));
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        try {
            if (!process.waitFor(settings.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new OcrException(OcrErrorCode.OCR_TIMEOUT,
                    "OCR took longer than " + settings.timeout().toSeconds() + "s");
            }
            writer.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
            String text = stdout.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (process.exitValue() != 0) {
                throw new OcrException(OcrErrorCode.OCR_FAILED,
                    "Tesseract exited with " + process.exitValue() + ": " + tail(stderr.getNow("")));
            }
            return text;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OcrException(OcrErrorCode.OCR_FAILED, "OCR was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            process.destroyForcibly();
            throw new OcrException(OcrErrorCode.OCR_FAILED, "Could not exchange data with Tesseract", e);
        }
    }

    /** True when the configured Tesseract executable can be started. */
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(settings.command(), "--version").redirectErrorStream(true).start();
            CompletableFuture.runAsync(() -> readAll(process.getInputStream()));
            return process.waitFor(VERSION_CHECK_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    List<String> command(int pageSegMode) {
        List<String> command = new ArrayList<>(List.of(
            settings.command(), "stdin", "stdout",
            "-l", settings.languages(),
            "--psm", String.valueOf(pageSegMode),
            "--oem", "1",
            "-c", "preserve_interword_spaces=1"));
        if (settings.tessdataDir() != null && !settings.tessdataDir().isBlank()) {
            command.add(1, "--tessdata-dir");
            command.add(2, settings.tessdataDir());
        }
        return command;
    }

    private static Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new OcrException(OcrErrorCode.OCR_FAILED, "Tesseract could not be started: " + e.getMessage(), e);
        }
    }

    private static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new OcrException(OcrErrorCode.OCR_FAILED, "Could not encode image for OCR", e);
        }
    }

    private static void writeAndClose(OutputStream stream, byte[] data) {
        try (OutputStream out = stream) {
            out.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readAll(InputStream stream) {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String tail(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= MAX_ERROR_CHARS ? trimmed : trimmed.substring(trimmed.length() - MAX_ERROR_CHARS);
    }
}
