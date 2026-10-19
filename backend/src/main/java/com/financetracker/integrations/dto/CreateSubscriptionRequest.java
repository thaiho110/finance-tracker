package com.financetracker.integrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create an outbound subscription (ADR-023 §2). */
public record CreateSubscriptionRequest(
    @NotBlank @Size(max = 2000) String targetUrl,
    @NotEmpty List<@NotBlank String> eventTypes) { }
