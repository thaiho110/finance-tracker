package com.financetracker.meals.dto;

import com.financetracker.meals.model.MealResponse;

import java.time.Instant;
import java.util.UUID;

/** One member's answer to a meal call (ADR-025 §3). */
public record AckView(UUID userId, MealResponse response, Instant respondedAt) { }
