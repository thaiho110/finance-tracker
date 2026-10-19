package com.financetracker.household.dto;

import java.time.Instant;

/** The shareable invite code and when it expires (ADR-022 §2). Shown once to the inviter to pass on. */
public record InviteResponse(String code, Instant expiresAt) { }
