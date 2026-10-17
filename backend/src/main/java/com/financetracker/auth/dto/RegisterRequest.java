package com.financetracker.auth.dto;

import com.financetracker.auth.validation.PasswordConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "New user registration request")
public record RegisterRequest(
    @Schema(description = "User email address", example = "newuser@example.com")
    @NotBlank @Email String email,

    @Schema(description = "Password (min 8 characters, must contain uppercase, lowercase, digit, and special character)",
             example = "MySecureP@ss123", minLength = 8)
    @NotBlank @PasswordConstraint String password
) { }
