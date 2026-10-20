package com.financetracker.integrations.dto;

import com.financetracker.integrations.model.WebhookSubscription;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** An outbound subscription (ADR-023 §2). The {@code secret} signs each delivery, so it is shown to the owner. */
public record SubscriptionResponse(
    UUID id,
    String targetUrl,
    String secret,
    List<String> eventTypes,
    boolean active,
    Instant createdAt) {

    public static SubscriptionResponse from(WebhookSubscription subscription) {
        List<String> types = Arrays.stream(subscription.getEventTypes().split(","))
            .map(String::trim).filter(type -> !type.isEmpty()).toList();
        return new SubscriptionResponse(subscription.getId(), subscription.getTargetUrl(),
            subscription.getSecret(), types, subscription.isActive(), subscription.getCreatedAt());
    }
}
