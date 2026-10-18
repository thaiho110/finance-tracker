package com.financetracker.admin.dto;

import com.financetracker.auth.model.RegistrationMode;
import jakarta.validation.constraints.NotNull;

/** Change the registration mode (ADR-016). */
public record UpdateRegistrationModeRequest(@NotNull RegistrationMode mode) { }
