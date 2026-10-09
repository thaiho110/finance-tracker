package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "New user registration request")
public record RegisterRequest(
    @Schema(description = "User email address", example = "newuser@example.com")
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,

    @Schema(description = "Password (min 8 characters)", example = "mySecurePassword123", minLength = 8)
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 100) String password
) { }
