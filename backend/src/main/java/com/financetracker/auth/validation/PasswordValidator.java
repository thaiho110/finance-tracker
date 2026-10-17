package com.financetracker.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<PasswordConstraint, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    // At least one uppercase, one lowercase, one digit, one special character
    private static final String UPPERCASE_PATTERN = ".*[A-Z].*";
    private static final String LOWERCASE_PATTERN = ".*[a-z].*";
    private static final String DIGIT_PATTERN = ".*\\d.*";
    private static final String SPECIAL_PATTERN = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?`~].*";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        // Length check
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        // Complexity checks
        if (!password.matches(UPPERCASE_PATTERN)) {
            return false;
        }
        if (!password.matches(LOWERCASE_PATTERN)) {
            return false;
        }
        if (!password.matches(DIGIT_PATTERN)) {
            return false;
        }
        if (!password.matches(SPECIAL_PATTERN)) {
            return false;
        }

        return true;
    }
}
