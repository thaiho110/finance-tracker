package com.financetracker.admin.dto;

import com.financetracker.auth.validation.PasswordConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "A new account created by an administrator")
public record CreateUserRequest(
    @NotBlank @Email String email,

    @Schema(description = "Initial password; same rules as registration")
    @NotBlank @PasswordConstraint String password,

    @Schema(description = "Role code; defaults to USER. Any other role needs ROLE_MANAGE", example = "USER", nullable = true)
    String role
) { }
