package com.financetracker.admin.dto;

import com.financetracker.auth.model.RegistrationMode;

/** The current registration mode (ADR-016). */
public record RegistrationModeResponse(RegistrationMode mode) { }
