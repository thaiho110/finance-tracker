package com.financetracker.access.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Turn a module on or off for the caller (ADR-020 §4). {@code enabled} is a boxed Boolean on purpose: a
 * missing JSON value then fails validation cleanly instead of tripping Jackson's primitive handling.
 */
public record SetModuleRequest(@NotNull Boolean enabled) { }
