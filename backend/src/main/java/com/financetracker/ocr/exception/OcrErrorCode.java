package com.financetracker.ocr.exception;

/**
 * Why a receipt could not be read. Returned to clients as the {@code code} property of the
 * problem response so the UI can show a specific message.
 */
public enum OcrErrorCode {
    /** The upload is not a JPG/PNG/PDF the server can decode. */
    UNSUPPORTED_FILE,
    /** OCR found almost no text — blurry, dark or not a document at all. */
    UNREADABLE,
    /** Text was found but no money amount, so it is probably not a receipt. */
    NOT_A_RECEIPT,
    /** OCR took longer than the configured timeout. */
    OCR_TIMEOUT,
    /** Too many receipts are being read at once; try again shortly. */
    OCR_BUSY,
    /** The OCR engine failed (not installed, crashed, bad configuration). */
    OCR_FAILED
}
