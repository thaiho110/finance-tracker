package com.financetracker.ocr.exception;

/**
 * A receipt could not be processed. Mapped to a problem response by the global exception handler.
 */
public class OcrException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OcrErrorCode code;

    public OcrException(OcrErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public OcrException(OcrErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public OcrErrorCode getCode() {
        return code;
    }
}
