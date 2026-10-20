package com.financetracker.integrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create an inbound webhook endpoint (ADR-023 §3). */
public record CreateEndpointRequest(@NotBlank @Size(max = 100) String name) { }
