package com.financetracker.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Send a message to the assistant (ADR-024). */
public record PostMessageRequest(@NotBlank @Size(max = 4000) String content) { }
