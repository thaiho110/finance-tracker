package com.financetracker.notifications.dto;

import com.financetracker.notifications.model.PushTransport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Register or refresh this device's push token (ADR-021 §4). */
public record RegisterPushTokenRequest(
    @NotBlank String client,
    @NotNull PushTransport transport,
    @NotBlank String token) { }
