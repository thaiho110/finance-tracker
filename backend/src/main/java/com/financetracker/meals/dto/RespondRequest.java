package com.financetracker.meals.dto;

import com.financetracker.meals.model.MealResponse;
import jakarta.validation.constraints.NotNull;

/** Answer a meal call (ADR-025 §3). */
public record RespondRequest(@NotNull MealResponse response) { }
