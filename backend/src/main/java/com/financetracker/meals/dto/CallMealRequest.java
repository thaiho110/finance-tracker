package com.financetracker.meals.dto;

import jakarta.validation.constraints.Size;

/** Start a meal call (ADR-025 §2). The message is optional; a default is used when blank. */
public record CallMealRequest(@Size(max = 200) String message) { }
