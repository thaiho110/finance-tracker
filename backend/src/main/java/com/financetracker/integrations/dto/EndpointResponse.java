package com.financetracker.integrations.dto;

import com.financetracker.integrations.model.WebhookEndpoint;

import java.time.Instant;
import java.util.UUID;

/**
 * An inbound webhook endpoint (ADR-023 §3). Carries the {@code token} (in the URL) and the {@code secret} (the
 * HMAC key) so the caller can configure the external system; both are shown here, not just on creation, because
 * they are needed to sign every call.
 */
public record EndpointResponse(
    UUID id,
    String name,
    String token,
    String secret,
    String eventType,
    boolean active,
    Instant createdAt) {

    public static EndpointResponse from(WebhookEndpoint endpoint) {
        return new EndpointResponse(endpoint.getId(), endpoint.getName(), endpoint.getToken(),
            endpoint.getSecret(), endpoint.getEventType(), endpoint.isActive(), endpoint.getCreatedAt());
    }
}
