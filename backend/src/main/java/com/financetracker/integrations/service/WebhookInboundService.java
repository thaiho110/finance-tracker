package com.financetracker.integrations.service;

import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.events.DomainEvents;
import com.financetracker.integrations.event.OutboundEvent;
import com.financetracker.integrations.model.WebhookEndpoint;
import com.financetracker.integrations.repository.WebhookEndpointRepository;
import com.financetracker.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Verifies an inbound webhook and turns it into an internal event (ADR-023 §3). Authenticated by the endpoint's
 * token (in the URL) plus an HMAC-SHA256 signature over {@code "<timestamp>.<body>"} within a short replay
 * window — never by a JWT. A valid call raises an {@link OutboundEvent} (for subscriptions) and an in-app
 * notification to the owner (the anchor use case).
 */
@Service
@RequiredArgsConstructor
public class WebhookInboundService {

    private static final long WINDOW_SECONDS = 300;
    /** Epoch-seconds are ~10 digits; cap the length so {@code parseLong} cannot overflow. */
    private static final int MAX_TIMESTAMP_DIGITS = 18;

    private final WebhookEndpointRepository endpoints;
    private final DomainEvents events;
    private final NotificationService notifications;

    public void handle(String token, String timestamp, String signature, byte[] body) {
        WebhookEndpoint endpoint = endpoints.findByToken(token)
            .filter(WebhookEndpoint::isActive)
            .orElseThrow(() -> new EntityNotFoundException("No such webhook"));

        String sent = timestamp == null ? "" : timestamp.strip();
        // Epoch seconds: digits only, and short enough that parseLong cannot overflow.
        if (sent.isEmpty() || sent.length() > MAX_TIMESTAMP_DIGITS || !sent.chars().allMatch(Character::isDigit)) {
            throw new AuthenticationException("Invalid webhook signature");
        }
        long sentAt = Long.parseLong(sent);
        if (Math.abs(Instant.now().getEpochSecond() - sentAt) > WINDOW_SECONDS) {
            throw new AuthenticationException("Invalid webhook signature");
        }

        String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        String expected = HmacSigner.sign(endpoint.getSecret(), sentAt + "." + payload);
        if (!HmacSigner.matches(expected, signature)) {
            throw new AuthenticationException("Invalid webhook signature");
        }

        UUID owner = endpoint.getOwnerUserId();
        events.publish(new OutboundEvent(owner, endpoint.getEventType(), payload));
        notifications.notify(owner, "webhook.triggered", "Webhook: " + endpoint.getName(),
            "An integration fired", null);
    }
}
