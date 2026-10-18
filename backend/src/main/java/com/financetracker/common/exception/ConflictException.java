package com.financetracker.common.exception;

/** The request clashes with existing data (for example a duplicate name); answered with 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
