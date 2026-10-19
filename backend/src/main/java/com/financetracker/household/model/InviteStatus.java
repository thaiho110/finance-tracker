package com.financetracker.household.model;

/** The lifecycle of a household invite (ADR-022 §2). */
public enum InviteStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}
