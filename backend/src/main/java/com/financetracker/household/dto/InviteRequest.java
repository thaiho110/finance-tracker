package com.financetracker.household.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Invite someone to the caller's household (ADR-022 §2). */
public record InviteRequest(@NotBlank @Email String email) { }
