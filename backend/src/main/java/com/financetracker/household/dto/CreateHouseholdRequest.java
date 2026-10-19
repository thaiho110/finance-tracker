package com.financetracker.household.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create a household (ADR-022 §2). */
public record CreateHouseholdRequest(@NotBlank @Size(max = 100) String name) { }
