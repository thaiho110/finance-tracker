package com.financetracker.integrations.model;

/** The outcome of an outbound webhook delivery (ADR-023 §2). */
public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
