package com.financetracker.notifications.dto;

import jakarta.validation.constraints.NotBlank;

/** Deregister this device's push token (on logout) (ADR-021 §4). */
public record RemovePushTokenRequest(@NotBlank String token) { }
