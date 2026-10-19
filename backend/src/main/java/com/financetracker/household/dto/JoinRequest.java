package com.financetracker.household.dto;

import jakarta.validation.constraints.NotBlank;

/** Accept an invite by its code (ADR-022 §2). */
public record JoinRequest(@NotBlank String code) { }
