package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login credentials")
public record AuthRequest(
    @Schema(description = "User email address", example = "user@example.com")
    @NotBlank @Email String email,

    @Schema(description = "User password", example = "mySecurePassword123")
    @NotBlank @Size(max = 100) String password
) { }
