package com.financetracker.household.dto;

import java.time.Instant;
import java.util.UUID;

/** One member of a household (ADR-022). */
public record MemberView(UUID userId, String email, Instant joinedAt) { }
