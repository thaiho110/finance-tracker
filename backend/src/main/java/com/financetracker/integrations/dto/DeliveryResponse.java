package com.financetracker.integrations.dto;

import com.financetracker.integrations.model.DeliveryStatus;
import com.financetracker.integrations.model.WebhookDelivery;

import java.time.Instant;
import java.util.UUID;

/** One outbound delivery attempt (ADR-023 §2). */
public record DeliveryResponse(
    UUID id,
    String eventType,
    DeliveryStatus status,
    int attempts,
    Integer responseCode,
    Instant lastAttemptAt,
    Instant createdAt) {

    public static DeliveryResponse from(WebhookDelivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.getEventType(), delivery.getStatus(),
            delivery.getAttempts(), delivery.getResponseCode(), delivery.getLastAttemptAt(), delivery.getCreatedAt());
    }
}
