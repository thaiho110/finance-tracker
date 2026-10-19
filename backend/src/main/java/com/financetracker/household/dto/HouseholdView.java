package com.financetracker.household.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A household and its members (ADR-022). */
public record HouseholdView(UUID id, String name, Instant createdAt, List<MemberView> members) { }
