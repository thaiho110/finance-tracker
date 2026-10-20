package com.financetracker.meals.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A meal call with who has responded so far (ADR-025). */
public record MealCallResponse(
    UUID id,
    UUID callerUserId,
    String message,
    Instant createdAt,
    List<AckView> responses) { }
